#!/bin/bash
set -e

NEW_VERSION=$1

if [ -z "$NEW_VERSION" ]; then
	echo "Usage: mise run publish <new_version>"
	exit 1
fi

# Ensure clean working directory (optional but recommended)
if [ -n "$(git status --porcelain)" ]; then
	echo "Error: Working directory is not clean. Commit or stash changes first."
	exit 1
fi

echo "Bumping version to $NEW_VERSION..."

# Update versionName
sed -i "s/versionName = \".*\"/versionName = \"$NEW_VERSION\"/" app/build.gradle.kts

# Increment versionCode
CURRENT_CODE=$(grep "versionCode =" app/build.gradle.kts | awk '{print $3}')
if [ -z "$CURRENT_CODE" ]; then
	echo "Error: Could not find versionCode in app/build.gradle.kts"
	exit 1
fi
NEW_CODE=$((CURRENT_CODE + 1))
sed -i "s/versionCode = $CURRENT_CODE/versionCode = $NEW_CODE/" app/build.gradle.kts

echo "Bumped version to $NEW_VERSION (code $NEW_CODE)"

# Commit
git add app/build.gradle.kts
git commit -m "Bump version to $NEW_VERSION"

# Tag
git tag "v$NEW_VERSION"

# Push
echo "Pushing changes and tag..."
git push origin main
git push origin "v$NEW_VERSION"

echo "Done! GitHub Actions should now start building v$NEW_VERSION."
