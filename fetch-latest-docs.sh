#!/bin/bash

set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 <input.yaml> <output-folder>"
  exit 1
fi

INPUT_YAML="$1"
OUTPUT_DIR="$2"

if ! command -v yq >/dev/null 2>&1; then
  echo "Error: 'yq' is required but not installed."
  exit 1
fi

mkdir -p "$OUTPUT_DIR"

############################################
# Step 1: Process micronaut-guides first
############################################

echo "📚 Processing micronaut-guides"

TMP_GUIDES_DIR=$(mktemp -d)
git clone --quiet --depth 1 --branch gh-pages "https://github.com/micronaut-projects/micronaut-guides.git" "$TMP_GUIDES_DIR"

GUIDES_LATEST="$TMP_GUIDES_DIR/latest"
GUIDES_DEST="$OUTPUT_DIR/micronaut-guides"
mkdir -p "$GUIDES_DEST"

echo "🔎 Copying filtered HTML files (groovy, java, kotlin)..."

find "$GUIDES_LATEST" -maxdepth 1 -type f \( \
  -name "*-groovy.html" -o \
  -name "*-java.html" -o \
  -name "*-kotlin.html" \
\) | while read -r html_file; do
  cp "$html_file" "$GUIDES_DEST/"
  echo "✅ Copied $(basename "$html_file") to $GUIDES_DEST/"
  # Generate metadata.json
  filename=$(basename "$html_file")
  metadata_file="$GUIDES_DEST/${filename}.metadata.json"
  url="https://guides.micronaut.io/latest/${filename}"
  echo "{\"metadataAttributes\":{\"customized_url_source\":\"$url\"}}" > "$metadata_file"
  echo "📝 Created metadata for $filename"
done

rm -rf "$TMP_GUIDES_DIR"

############################################
# Step 2: Process all repositories from YAML
############################################

echo "📄 Parsing YAML and cloning repositories..."

yq eval '.. | select(has("slug")) | .slug' "$INPUT_YAML" | while read -r slug; do
  echo "🔍 Processing $slug"

  TMP_DIR=$(mktemp -d)
  git clone --quiet --depth 1 --branch gh-pages "https://github.com/micronaut-projects/$slug.git" "$TMP_DIR"

  GUIDE_DIR="$TMP_DIR/latest/guide"
  DEST_DIR="$OUTPUT_DIR/$slug"
  mkdir -p "$DEST_DIR"

  for file in index.html configurationreference.html; do
    SRC="$GUIDE_DIR/$file"
    if [[ -f "$SRC" ]]; then
      cp "$SRC" "$DEST_DIR/"
      echo "✅ Copied $file to $DEST_DIR/"
      # Generate metadata.json
      metadata_file="$DEST_DIR/${file}.metadata.json"
      url="https://micronaut-projects.github.io/${slug}/latest/guide/${file}"
      echo "{\"metadataAttributes\":{\"customized_url_source\":\"$url\"}}" > "$metadata_file"
      echo "📝 Created metadata for $file"
    else
      echo "⚠️  Warning: $file not found in $slug"
    fi
  done

  rm -rf "$TMP_DIR"
done

############################################
# Step 3: Process graal-io-website GDK modules
############################################

echo "🧩 Processing GDK Guides"

TMP_GDK_DIR=$(mktemp -d)

git clone --quiet --depth 1 --branch master "https://github.com/graalvm/graal-io-website.git" "$TMP_GDK_DIR"

GDK_MODULES_DIR="$TMP_GDK_DIR/gdk/gdk-modules"
GDK_OUTPUT_DIR="$OUTPUT_DIR/gdk-guides"
mkdir -p "$GDK_OUTPUT_DIR"

find "$GDK_MODULES_DIR" -type f -name index.html | while read -r index_file; do
  MODULE_DIR=$(dirname "$index_file")
  MODULE_NAME=$(basename "$MODULE_DIR")

  DEST_FILE="$GDK_OUTPUT_DIR/${MODULE_NAME}.html"
  cp "$index_file" "$DEST_FILE"

  echo "✅ Copied $MODULE_NAME/index.html to $DEST_FILE"
  # Generate metadata.json
  metadata_file="$GDK_OUTPUT_DIR/${MODULE_NAME}.html.metadata.json"
  url="https://graalvm.github.io/graal-io-website/gdk/gdk-modules/${MODULE_NAME}/index.html"
  echo "{\"metadataAttributes\":{\"customized_url_source\":\"$url\"}}" > "$metadata_file"
  echo "📝 Created metadata for ${MODULE_NAME}.html"
done

rm -rf "$TMP_GDK_DIR"

echo "🎉 All done."
