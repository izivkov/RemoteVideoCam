#!/bin/bash

# release.sh - Automates the release process for RemoteVideoCam

# Debug release process
if [ "$1" == "debug" ]; then
    echo "🐞 Processing Debug Build..."
    
    APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
    
    # Build if not exists
    if [ ! -f "$APK_PATH" ]; then
        echo "🔨 Building Debug APK..."
        ./gradlew assembleDebug
    fi
    
    if [ ! -f "$APK_PATH" ]; then
        echo "❌ Error: Debug APK not found at $APK_PATH"
        exit 1
    fi

    # Check for gh availability
    if ! command -v gh &> /dev/null; then
         echo "❌ GitHub CLI (gh) is not installed. Please install it to upload to GitHub."
         exit 1
    fi

    echo "📤 Uploading to GitHub Releases (tag: debug)..."
    
    # Delete existing 'debug' tag/release if it exists (swallow errors)
    gh release delete debug -y --cleanup-tag &> /dev/null || true
    
    # Create new release with the APK
    gh release create debug "$APK_PATH" \
       --prerelease \
       --title "Latest Debug Build" \
       --notes "Debug build uploaded via release.sh on $(date)"
       
    if [ $? -eq 0 ]; then
       echo ""
       echo "✅ Debug APK Uploaded to GitHub!"
       # Attempt to get the repository URL for a clickable link
       REPO=$(gh repo view --json url -q .url)
       echo "🔗 Release: $REPO/releases/tag/debug"
    else
       echo "❌ GitHub upload failed."
    fi
    
    echo "📂 Local file location:"
    echo "   $(pwd)/$APK_PATH"
    
    exit 0
fi

# Normal release process (new release)
if [ -z "$1" ] || [[ "$1" == -* ]]; then
    echo "Usage: ./release.sh <version_name> [OR debug]"
    echo ""
    echo "Examples:"
    echo "  ./release.sh 3.6               # Create new release"
    echo "  ./release.sh debug             # Upload debug APK"
    exit 1
fi
# Ensure we are on the main branch
CURRENT_BRANCH=$(git branch --show-current)
if [ "$CURRENT_BRANCH" != "main" ]; then
    echo "🔄 Switching to main branch..."
    git checkout main
    git pull origin main
    CURRENT_BRANCH="main"
fi

# Check if gh CLI is installed
if ! command -v gh &> /dev/null; then
    echo "⚠️  Warning: GitHub CLI (gh) is not installed. GitHub release will not be created."
    echo "   Install it from: https://cli.github.com/"
    GH_AVAILABLE=false
else
    GH_AVAILABLE=true
fi

VERSION_NAME=${1#v}
# Calculate version code by removing dots (e.g., 25.4 -> 254)
VERSION_CODE=$(echo $VERSION_NAME | sed 's/\.//g')

echo "🚀 Preparing release for version $VERSION_NAME (Code: $VERSION_CODE)..."

# 1. Update app/build.gradle.kts (Kotlin DSL)
echo "📝 Updating app/build.gradle.kts..."
sed -i "s/versionCode = .*/versionCode = $VERSION_CODE/" app/build.gradle.kts
sed -i "s/versionName = .*/versionName = \"$VERSION_NAME\"/" app/build.gradle.kts


# 2. Create latest.txt version metadata
echo "📄 Creating latest.txt with version $VERSION_NAME..."
echo "$VERSION_NAME" > latest.txt

# 3. Update F-Droid Metadata (Fastlane)
CHANGELOG_PATH="fastlane/metadata/android/en-US/changelogs/${VERSION_CODE}.txt"
echo "📂 Creating F-Droid changelog at $CHANGELOG_PATH..."

if [ -f "RELEASE_NOTES.md" ]; then
    # Use content from RELEASE_NOTES.md
    cat RELEASE_NOTES.md > "$CHANGELOG_PATH"
else
    echo "New release $VERSION_NAME" > "$CHANGELOG_PATH"
fi

# 4. Git Operations
echo "💾 Committing changes..."

git add app/build.gradle.kts "$CHANGELOG_PATH" gradle.properties release.sh .github/workflows/build-apk.yml README.md latest.txt RELEASE_NOTES.md fastlane/metadata/android/en-US/full_description.txt
git commit -m "Release v$VERSION_NAME"

echo "🏷️ Tagging release..."
git tag -a "v$VERSION_NAME" -m "Release version $VERSION_NAME"

echo "📤 Pushing to GitHub..."
CURRENT_BRANCH=$(git branch --show-current)
git push origin "$CURRENT_BRANCH"
git push origin "v$VERSION_NAME"

# Create GitHub Release
if [ "$GH_AVAILABLE" = true ]; then
    echo "🎁 Creating GitHub release v$VERSION_NAME..."
    if [ -f "RELEASE_NOTES.md" ]; then
        gh release create "v$VERSION_NAME" --title "Release v$VERSION_NAME" --notes-file "RELEASE_NOTES.md"
    else
        gh release create "v$VERSION_NAME" --title "Release v$VERSION_NAME" --notes "New release $VERSION_NAME"
    fi
fi

# 4. Update master branch for F-Droid
if [ "$CURRENT_BRANCH" == "main" ]; then
    echo "🔄 Merging main into master..."
    git checkout master
    git pull origin master
    if git merge main --no-edit; then
        git push origin master
    else
        echo "❌ Error: Merge into master failed due to conflicts."
        echo "   Please resolve conflicts manually on the master branch."
        git merge --abort
    fi
    git checkout main
fi

echo "✅ Release process initiated! The GitHub Action will now build and upload the APK."

