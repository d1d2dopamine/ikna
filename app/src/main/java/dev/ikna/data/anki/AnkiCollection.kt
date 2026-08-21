package dev.ikna.data.anki

import android.database.sqlite.SQLiteDatabase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** One card template of a notetype: the two sides, before a field is filled in. */
internal data class Template(val ordinal: Int, val question: String, val answer: String)

/** A notetype: its field names in order, its templates, and whether it is cloze. */
internal data class Model(val fields: List<String>, val templates: List<Template>, val cloze: Boolean)

/** What a collection says about its notetypes and decks, however it says it. */
internal data class CollectionShape(
    val models: Map<Long, Model>,
    val deckNames: Map<Long, String>,
    val schema: String
)

/**
 * Where an Anki collection keeps its notetypes and decks -- in two shapes.
 *
 * Until Anki 2.1.28 both were JSON inside single columns of the `col` row. After
 * that they moved into their own tables, and those columns were left empty. An
 * export from any recent Anki is the second shape unless the person exporting it
 * ticked "Support older Anki versions", and an app that reads only the first
 * shape tells everybody else that their file is damaged. It is not damaged; it is
 * current.
 *
 * Both shapes are read here, chosen by what the file actually contains rather
 * than by a version number: the JSON columns if they hold notetypes, the tables
 * otherwise. Neither path writes anything, and the decision happens before the
 * import transaction opens, so a collection this cannot read is refused with
 * "not supported" and no half-written deck.
 */
internal object AnkiCollection {

    /** Notetypes and decks as JSON in the `col` row: Anki up to 2.1.27. */
    const val CLASSIC = "classic"

    /** Notetypes and decks in their own tables: Anki 2.1.28 and later. */
    const val TABLES = "tables"

    fun read(
        database: SQLiteDatabase,
        json: Json,
        models: String,
        decks: String
    ): CollectionShape =
        classic(json, models, decks)
            ?: tables(database)
            ?: throw AnkiImportException(AnkiImportError.UNSUPPORTED_COLLECTION)

    private fun classic(json: Json, models: String, decks: String): CollectionShape? =
        runCatching {
            if (models.isBlank() || decks.isBlank()) return@runCatching null
            val parsedModels = parseModels(json, models)
            if (parsedModels.isEmpty()) return@runCatching null
            CollectionShape(parsedModels, parseDecks(json, decks), CLASSIC)
        }.getOrNull()

    /**
     * The same information out of `notetypes`, `fields`, `templates` and
     * `decks`.
     *
     * The names are columns, so they are read as columns. What is left in a
     * protobuf blob is exactly two things: the notetype's kind, which is how a
     * cloze notetype is recognised, and the two template sides. A blob that
     * cannot be read leaves a notetype with no templates, which the importer
     * already handles by reading the note's fields in order -- the same thing it
     * does for a note whose notetype is missing altogether.
     */
    private fun tables(database: SQLiteDatabase): CollectionShape? = runCatching {
        if (!hasTable(database, "notetypes")) return@runCatching null

        val fields = HashMap<Long, MutableList<String>>()
        runCatching {
            database.rawQuery("SELECT ntid, name FROM fields ORDER BY ntid, ord", null).use { cursor ->
                while (cursor.moveToNext()) {
                    fields.getOrPut(cursor.getLong(0)) { ArrayList() } +=
                        cursor.getString(1).orEmpty()
                }
            }
        }

        val templates = HashMap<Long, MutableList<Template>>()
        runCatching {
            database.rawQuery("SELECT ntid, ord, config FROM templates ORDER BY ntid, ord", null)
                .use { cursor ->
                    while (cursor.moveToNext()) {
                        val config = runCatching { cursor.getBlob(2) }.getOrNull() ?: ByteArray(0)
                        templates.getOrPut(cursor.getLong(0)) { ArrayList() } += Template(
                            ordinal = cursor.getInt(1),
                            question = AnkiProto.text(config, TEMPLATE_QUESTION).orEmpty(),
                            answer = AnkiProto.text(config, TEMPLATE_ANSWER).orEmpty()
                        )
                    }
                }
        }

        val models = LinkedHashMap<Long, Model>()
        database.rawQuery("SELECT id, config FROM notetypes", null).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val config = runCatching { cursor.getBlob(1) }.getOrNull() ?: ByteArray(0)
                models[id] = Model(
                    fields = fields[id].orEmpty(),
                    templates = templates[id].orEmpty(),
                    cloze = AnkiProto.varint(config, NOTETYPE_KIND) == KIND_CLOZE
                )
            }
        }
        if (models.isEmpty()) return@runCatching null

        val deckNames = LinkedHashMap<Long, String>()
        runCatching {
            database.rawQuery("SELECT id, name FROM decks", null).use { cursor ->
                while (cursor.moveToNext()) {
                    // Nesting is stored with a unit separator where the interface
                    // shows "::". The deck's title is what the user will look for
                    // in a list of decks, so it is shown their way.
                    deckNames[cursor.getLong(0)] =
                        cursor.getString(1).orEmpty().replace(NAME_SEPARATOR, "::")
                }
            }
        }

        CollectionShape(models, deckNames, TABLES)
    }.getOrNull()

    private fun hasTable(database: SQLiteDatabase, name: String): Boolean =
        database.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1",
            arrayOf(name)
        ).use { cursor -> cursor.moveToFirst() }

    private fun parseModels(json: Json, raw: String): Map<Long, Model> {
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return emptyMap()
        return root.mapNotNull model@ { (id, element) ->
            val body = element as? JsonObject ?: return@model null
            val fields = (body["flds"] as? JsonArray)?.mapNotNull field@ { field ->
                (field as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull
            }.orEmpty()
            val templates = (body["tmpls"] as? JsonArray)?.mapNotNull template@ { item ->
                val template = item as? JsonObject ?: return@template null
                Template(
                    ordinal = template["ord"]?.jsonPrimitive?.intOrNull ?: 0,
                    question = template["qfmt"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    answer = template["afmt"]?.jsonPrimitive?.contentOrNull.orEmpty()
                )
            }.orEmpty()
            id.toLongOrNull()?.let { key ->
                key to Model(
                    fields = fields,
                    templates = templates,
                    cloze = body["type"]?.jsonPrimitive?.intOrNull == 1
                )
            }
        }.toMap()
    }

    private fun parseDecks(json: Json, raw: String): Map<Long, String> {
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return emptyMap()
        return root.mapNotNull { (id, element) ->
            val body = element as? JsonObject ?: return@mapNotNull null
            val name = body["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            id.toLongOrNull()?.let { it to name }
        }.toMap()
    }

    /** `NotetypeConfig.kind`, and the value of that field that means cloze. */
    private const val NOTETYPE_KIND = 1
    private const val KIND_CLOZE = 1L

    /** `CardTemplateConfig.q_format` and `.a_format`. */
    private const val TEMPLATE_QUESTION = 1
    private const val TEMPLATE_ANSWER = 2

    private const val NAME_SEPARATOR = "\u001F"
}
