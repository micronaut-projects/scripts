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

#echo "📄 Processing OCI Micronaut Documentation..."
#
#OCI_MICRONAUT_DIR="/Users/sdelamo/scm/oci-micronaut/micronaut-oci-service-root/build/docs"
#DEST_DIR="$OUTPUT_DIR/oci-micronaut"
#GUIDE_PATH="/guide"
#GUIDE_DIR="${OCI_MICRONAUT_DIR}${GUIDE_PATH}"
#mkdir -p "$DEST_DIR"
#find "$OCI_MICRONAUT_DIR/api" -type f -name "*.html" | while read -r src_file; do
#  rel_path="${src_file#$OCI_MICRONAUT_DIR/}"
#  dest_file="$DEST_DIR/$rel_path"
#  mkdir -p "$(dirname "$dest_file")"
#  cp "$src_file" "$dest_file"
#  pathToApiHtmlFile="$rel_path"
#  url="https://docs.gcn.oraclecorp.com/pegasus/latest/${pathToApiHtmlFile}"
#  metadata_file="${dest_file}.metadata.json"
#  echo "{\"metadataAttributes\":{\"customized_url_source\":\"$url\"}}" > "$metadata_file"
#done
#for file in index.html configurationreference.html; do
#  SRC="$GUIDE_DIR/$file"
#  if [[ -f "$SRC" ]]; then
#    cp "$SRC" "$DEST_DIR/"
#    echo "✅ Copied $file to $DEST_DIR/"
#    # Generate metadata.json
#    metadata_file="$DEST_DIR/${file}.metadata.json"
#    url="https://docs.gcn.oraclecorp.com/pegasus/latest/guide/${file}"
#    echo "{\"metadataAttributes\":{\"customized_url_source\":\"$url\"}}" > "$metadata_file"
#    echo "📝 Created metadata for $file"
#  else
#    echo "⚠️  Warning: $file not found in $slug"
#  fi
#done

############################################
# Step 1: Process all repositories from YAML
############################################

echo "📄 Parsing YAML and cloning repositories..."

yq eval '.. | select(has("slug")) | .slug' "$INPUT_YAML" | while read -r slug; do
  # Skip micronaut-maven-plugin
  if [[ "$slug" == "micronaut-maven-plugin" ]]; then
    echo "⏩ Skipping $slug"
    continue
  fi
  echo "🔍 Processing $slug"

  TMP_DIR=$(mktemp -d)
  git clone --quiet --depth 1 --branch gh-pages "https://github.com/micronaut-projects/$slug.git" "$TMP_DIR"

  [ "$slug" = "micronaut-gradle-plugin" ] && GUIDE_PATH="/latest" || GUIDE_PATH="/latest/guide"
  GUIDE_DIR="${TMP_DIR}${GUIDE_PATH}"

  DEST_DIR="$OUTPUT_DIR/$slug"
  mkdir -p "$DEST_DIR"


  # Copy Javadoc HTML
  API_SRC_DIR="${TMP_DIR}/latest"
  if [ -d "$API_SRC_DIR/api" ]; then
    find "$API_SRC_DIR/api" -type f -name "*.html" | while read -r src_file; do
      rel_path="${src_file#$API_SRC_DIR/}"               # e.g., api/index.html
      dest_file="$DEST_DIR/$rel_path"
      mkdir -p "$(dirname "$dest_file")"
      cp "$src_file" "$dest_file"
      pathToApiHtmlFile="$rel_path"
      url="https://micronaut-projects.github.io/${slug}/latest/${pathToApiHtmlFile}"
      metadata_file="${dest_file}.metadata.json"
      echo "{\"metadataAttributes\":{\"customized_url_source\":\"$url\"}}" > "$metadata_file"
    done
  else
    echo "🔸 No 'api' folder for $slug, skipping Javadoc HTML copy."
  fi

  for file in index.html configurationreference.html; do
    SRC="$GUIDE_DIR/$file"
    if [[ -f "$SRC" ]]; then
      cp "$SRC" "$DEST_DIR/"
      echo "✅ Copied $file to $DEST_DIR/"
      # Generate metadata.json
      metadata_file="$DEST_DIR/${file}.metadata.json"
      url="https://micronaut-projects.github.io/${slug}${GUIDE_PATH}/${file}"
      echo "{\"metadataAttributes\":{\"customized_url_source\":\"$url\"}}" > "$metadata_file"
      echo "📝 Created metadata for $file"
    else
      echo "⚠️  Warning: $file not found in $slug"
    fi
  done

  rm -rf "$TMP_DIR"
done

############################################
# Step 2: Process all repositories from YAML
############################################

echo "📄 cloning micronaut-docs repository"

TMP_DOCS_DIR=$(mktemp -d)
git clone --quiet --depth 1 --branch gh-pages "https://github.com/micronaut-projects/micronaut-docs.git" "$TMP_DOCS_DIR"

DOCS_LATEST="$TMP_DOCS_DIR/latest/guide"
DOCS_DEST="$OUTPUT_DIR/micronaut-core"

mkdir -p "$DOCS_DEST"

for file in index.html configurationreference.html; do
  SRC="$DOCS_LATEST/$file"
  if [[ -f "$SRC" ]]; then
    cp "$SRC" "$DOCS_DEST/"
    echo "✅ Copied $file to $DOCS_DEST/"
    # Generate metadata.json
    metadata_file="$DOCS_DEST/${file}.metadata.json"
    url="https://docs.micronaut.io/latest/guide/${file}"
    echo "{\"metadataAttributes\":{\"customized_url_source\":\"$url\"}}" > "$metadata_file"
    echo "📝 Created metadata for $file"
  else
      echo "⚠️  Warning: $file not found"
  fi
done

rm -rf "$TMP_DOCS_DIR"

############################################
# Step 3: Process micronaut-guides first
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
# Step 4: Process graal-io-website GDK modules
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


  case "$MODULE_DIR" in
    *gdk-modules/*)
      REL="gdk-modules/${MODULE_DIR#*gdk-modules/}"
      url="https://graal.cloud/gdk/${REL}/"
      echo "{\"metadataAttributes\":{\"customized_url_source\":\"$url\"}}" > "$metadata_file"
      echo "📝 Created metadata for ${MODULE_NAME}.html"
      ;;
    *)
      echo "gdk-modules/ not found in MODULE_DIR" >&2
      ;;
  esac
done

rm -rf "$TMP_GDK_DIR"

echo "🎉 All done."
