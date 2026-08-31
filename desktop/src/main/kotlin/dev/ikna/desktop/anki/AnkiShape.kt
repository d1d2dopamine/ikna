package dev.ikna.desktop.anki

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** One card template of a notetype: the two sides, before a field is filled in. */
internal data class Template(val ordinal: Int, val question: String, val answer: String)

/** A notetype: its field names in order, its templates, and whether it is cloze. */
internal data class Model(
    val fields: List<String>,
    val templates: List<Template>,
    val cloze: Boolean
)

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
 * that they moved into their own tables and those columns were left empty. An
 * export from any recent Anki is the second shape unless the person exporting it
 * ticked "Support older Anki versions", and a reader that knows only the first
 * shape tells everybody else their file is damaged. It is not damaged; it is
 * current.
 *
 * This is the phone's AnkiCollection with the Cursor replaced by [AnkiSql]. The
 * decisions are deliberately identical, down to the constants: the two readers
 * have to agree about what a file means, or the same .apkg would import as two
 * different decks depending on which machine opened it.
 */
internal object AnkiShape {

    /** Notetypes and decks as JSON in the `col` row: Anki up to 2.1.27. */
    const val CLASSIC = "classic"

    /** Notetypes and decks in their own tables: Anki 2.1.28 and later. */
    const val TABLES = "tables"

    /** No notetypes anywhere: each note read by the order of its own fields. */
    const val FIELDS = "fields"

    fun read(sql: AnkiSql, json: Json, models: String, decks: String): CollectionShape =
        classic(json, models, decks)
            ?: tables(sql)
            ?: fields(sql, json, decks)

    private fun classic(json: Json, models: String, decks: String): CollectionShape? =
        runCatching {
            if (models.isBlank() || decks.isBlank()) return@runCatching null
            val parsed = parseModels(json, models)
            if (parsed.isEmpty()) return@runCatching null
            CollectionShape(parsed, parseDecks(json, decks), CLASSIC)
        }.getOrNull()

    /**
     * The same information out of `notetypes`, `fields`, `templates` and `decks`.
     *
     * The names are columns, so they are read as columns. What is left in a
     * protobuf blob is exactly two things: the notetype's kind, which is how a
     * cloze notetype is recognised, and the two template sides. A blob that
     * cannot be read leaves a notetype with no templates, which the importer
     * already handles by reading the note's fields in order.
     */
    private fun tables(sql: AnkiSql): CollectionShape? = runCatching {
        if (!sql.hasTable("notetypes")) return@runCatching null

        val fields = HashMap<Long, MutableList<String>>()
        sql.rows("SELECT ntid, name FROM fields ORDER BY ntid, ord") { row ->
            fields.getOrPut(AnkiSql.long(row, 0)) { ArrayList() } += AnkiSql.text(row, 1)
            1
        }

        val templates = HashMap<Long, MutableList<Template>>()
        sql.rows("SELECT ntid, ord, config FROM templates ORDER BY ntid, ord") { row ->
            val config = AnkiSql.blob(row, 2)
            templates.getOrPut(AnkiSql.long(row, 0)) { ArrayList() } += Template(
                ordinal = AnkiSql.int(row, 1),
                question = AnkiProto.text(config, TEMPLATE_QUESTION).orEmpty(),
                answer = AnkiProto.text(config, TEMPLATE_ANSWER).orEmpty()
            )
            1
        }

        val models = LinkedHashMap<Long, Model>()
        sql.rows("SELECT id, config FROM notetypes") { row ->
            val id = AnkiSql.long(row, 0)
            val config = AnkiSql.blob(row, 1)
            models[id] = Model(
                fields = fields[id].orEmpty(),
                templates = templates[id].orEmpty(),
                cloze = AnkiProto.varint(config, NOTETYPE_KIND) == KIND_CLOZE
            )
            1
        }
        if (models.isEmpty()) return@runCatching null

        CollectionShape(models, deckNames(sql), TABLES)
    }.getOrNull()

    /**
     * Last resort: the notes, and nothing about how they were meant to look.
     *
     * A file can reach here for good reasons -- a shape newer than this reader,
     * a protobuf field renumbered, a blob that lost a byte -- and in all of them
     * the notes are still plain text in a table this can read. Only the absence
     * of notes or cards is a real refusal.
     */
    private fun fields(sql: AnkiSql, json: Json, decks: String): CollectionShape {
        if (!sql.hasTable("notes") || !sql.hasTable("cards")) {
            throw AnkiImportException(AnkiImportError.UNSUPPORTED_COLLECTION)
        }
        val names = LinkedHashMap<Long, String>()
        names += deckNames(sql)
        if (names.isEmpty()) {
            names += runCatching { parseDecks(json, decks) }.getOrDefault(emptyMap())
        }
        return CollectionShape(emptyMap(), names, FIELDS)
    }

    /**
     * Deck names out of the `decks` table.
     *
     * Nesting is stored with a unit separator where the interface shows "::".
     * The title is what somebody will look for in a list of decks, so it is
     * shown their way.
     */
    private fun deckNames(sql: AnkiSql): Map<Long, String> {
        val names = LinkedHashMap<Long, String>()
        runCatching {
            sql.rows("SELECT id, name FROM decks") { row ->
                names[AnkiSql.long(row, 0)] =
                    AnkiSql.text(row, 1).replace(NAME_SEPARATOR, "::")
                1
            }
        }
        return names
    }

    private fun parseModels(json: Json, raw: String): Map<Long, Model> {
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return emptyMap()
        return root.mapNotNull model@{ (id, element) ->
            val body = element as? JsonObject ?: return@model null
            val fields = (body["flds"] as? JsonArray)?.mapNotNull field@{ field ->
                (field as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull
            }.orEmpty()
            val templates = (body["tmpls"] as? JsonArray)?.mapNotNull template@{ item ->
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
