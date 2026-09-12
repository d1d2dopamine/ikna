#!/usr/bin/env bash
set -euo pipefail

# Reliable large-file fetch for CI corpus downloads.
#
# GitHub-hosted runners occasionally get TLS/HTTP connection resets from public
# corpus mirrors. curl's plain --retry does not retry every transport error, and
# writing straight to the destination can leave a truncated archive behind.
# This helper retries every curl failure, resumes a partial file when the server
# supports byte ranges, forces HTTP/1.1 (more reliable with a few corpus/CDN
# endpoints), and only renames a completed download into place.
#
# Usage:
#   fetch-url.sh URL DEST [HEADERS]
#
# Optional environment variables:
#   IKNA_FETCH_ATTEMPTS       default 10
#   IKNA_FETCH_DELAY_SECONDS  default 5 (linear backoff, capped at 60 s)
#   IKNA_FETCH_CONNECT_TIMEOUT default 30

if [ "$#" -lt 2 ] || [ "$#" -gt 3 ]; then
  echo "usage: $0 URL DEST [HEADERS]" >&2
  exit 2
fi

url=$1
dest=$2
headers=${3:-}
attempts=${IKNA_FETCH_ATTEMPTS:-10}
delay=${IKNA_FETCH_DELAY_SECONDS:-5}
connect_timeout=${IKNA_FETCH_CONNECT_TIMEOUT:-30}

case "$attempts" in (*[!0-9]*|'') echo "IKNA_FETCH_ATTEMPTS must be a positive integer" >&2; exit 2;; esac
case "$delay" in (*[!0-9]*|'') echo "IKNA_FETCH_DELAY_SECONDS must be a non-negative integer" >&2; exit 2;; esac
case "$connect_timeout" in (*[!0-9]*|'') echo "IKNA_FETCH_CONNECT_TIMEOUT must be a positive integer" >&2; exit 2;; esac
[ "$attempts" -gt 0 ] || { echo "IKNA_FETCH_ATTEMPTS must be > 0" >&2; exit 2; }
[ "$connect_timeout" -gt 0 ] || { echo "IKNA_FETCH_CONNECT_TIMEOUT must be > 0" >&2; exit 2; }

mkdir -p "$(dirname "$dest")"
part="${dest}.part"
header_part=""
if [ -n "$headers" ]; then
  mkdir -p "$(dirname "$headers")"
  header_part="${headers}.part"
fi

cleanup_headers() {
  if [ -n "$header_part" ]; then
    rm -f "$header_part"
  fi
}
trap cleanup_headers EXIT

for ((attempt = 1; attempt <= attempts; attempt++)); do
  resume=()
  if [ -s "$part" ]; then
    resume=(--continue-at -)
    echo "Fetch attempt $attempt/$attempts: resuming $(basename "$dest") at $(wc -c < "$part" | tr -d ' ') bytes"
  else
    rm -f "$part"
    echo "Fetch attempt $attempt/$attempts: $url"
  fi

  curl_args=(
    --fail
    --location
    --silent
    --show-error
    --http1.1
    --connect-timeout "$connect_timeout"
    --keepalive-time 15
    --output "$part"
  )
  if [ -n "$header_part" ]; then
    rm -f "$header_part"
    curl_args+=(--dump-header "$header_part")
  fi
  curl_args+=("${resume[@]}")

  set +e
  curl "${curl_args[@]}" "$url"
  status=$?
  set -e

  if [ "$status" -eq 0 ]; then
    if [ ! -s "$part" ]; then
      echo "Downloaded file is empty: $url" >&2
      status=1
    else
      mv -f "$part" "$dest"
      if [ -n "$headers" ]; then
        mv -f "$header_part" "$headers"
      fi
      bytes=$(wc -c < "$dest" | tr -d ' ')
      echo "Fetched $dest ($bytes bytes)"
      exit 0
    fi
  fi

  # curl 33 means the endpoint would not resume the partial response. Throw the
  # partial away so the next attempt is a clean request instead of failing on
  # the same Range header forever.
  if [ "$status" -eq 33 ]; then
    echo "Server cannot resume this response; restarting it on the next attempt." >&2
    rm -f "$part"
  fi
  cleanup_headers

  if [ "$attempt" -eq "$attempts" ]; then
    echo "Failed to fetch $url after $attempts attempts (last curl exit $status)." >&2
    exit "$status"
  fi

  wait_seconds=$((delay * attempt))
  if [ "$wait_seconds" -gt 60 ]; then wait_seconds=60; fi
  echo "curl exited $status; retrying in ${wait_seconds}s..." >&2
  sleep "$wait_seconds"
done
