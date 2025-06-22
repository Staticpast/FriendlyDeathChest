#!/bin/bash

# FriendlyDeathChest Plugin Build and Deploy Script
# This script detects changes, increments versions, builds the plugin, and deploys it to the server

set -e
set -o pipefail

# --- Configuration ---
readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_DIR="$SCRIPT_DIR"
readonly POM_FILE="$PROJECT_DIR/pom.xml"
readonly PLUGINS_DIR="$SCRIPT_DIR/../server/plugins"
readonly VERSION_CACHE_FILE="$PROJECT_DIR/.last_build_version"
readonly CHANGE_CACHE_FILE="$PROJECT_DIR/.last_build_hash"

# Plugin configuration
readonly PLUGIN_NAME="FriendlyDeathChest"
readonly PLUGIN_ARTIFACT_ID="FriendlyDeathChest"

# --- Logging Functions ---
log_info() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] INFO: $*"
}

log_error() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] ERROR: $*" >&2
}

log_warning() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] WARNING: $*" >&2
}

log_success() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] SUCCESS: $*"
}

# --- Utility Functions ---
check_dependencies() {
    log_info "Checking dependencies..."
    
    local missing_deps=()
    
    if ! command -v mvn >/dev/null 2>&1; then
        missing_deps+=("maven")
    fi
    
    if ! command -v git >/dev/null 2>&1; then
        missing_deps+=("git")
    fi
    
    if ! command -v xmlstarlet >/dev/null 2>&1; then
        log_warning "xmlstarlet not found. Installing via Homebrew..."
        if command -v brew >/dev/null 2>&1; then
            brew install xmlstarlet
        else
            missing_deps+=("xmlstarlet")
        fi
    fi
    
    if [[ ${#missing_deps[@]} -gt 0 ]]; then
        log_error "Missing dependencies: ${missing_deps[*]}"
        log_error "Please install the missing dependencies and try again."
        exit 1
    fi
    
    log_info "Dependencies check passed."
}

get_current_version() {
    xmlstarlet sel -t -v "//*[local-name()='project']/*[local-name()='version']" "$POM_FILE" 2>/dev/null || {
        grep -o '<version>[^<]*</version>' "$POM_FILE" | head -1 | sed 's/<version>\(.*\)<\/version>/\1/' || {
            log_error "Failed to read version from $POM_FILE"
            exit 1
        }
    }
}

increment_version() {
    local current_version="$1"
    local version_type="${2:-patch}"
    
    if [[ $current_version =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
        local major="${BASH_REMATCH[1]}"
        local minor="${BASH_REMATCH[2]}"
        local patch="${BASH_REMATCH[3]}"
        
        case "$version_type" in
            major)
                major=$((major + 1))
                minor=0
                patch=0
                ;;
            minor)
                minor=$((minor + 1))
                patch=0
                ;;
            patch|*)
                patch=$((patch + 1))
                ;;
        esac
        
        echo "${major}.${minor}.${patch}"
    else
        log_error "Invalid version format: $current_version (expected: major.minor.patch)"
        exit 1
    fi
}

update_version() {
    local new_version="$1"
    
    log_info "Updating plugin version to $new_version using Maven versions plugin..."
    
    # Use Maven versions plugin to update version
    mvn versions:set -DnewVersion="$new_version" -DgenerateBackupPoms=false -q || {
        log_error "Failed to update version using Maven versions plugin"
        exit 1
    }
    
    log_info "Successfully updated version to $new_version"
}

calculate_project_hash() {
    # Calculate hash of all source files and configuration
    find "$PROJECT_DIR/src" "$PROJECT_DIR/pom.xml" -type f \( -name "*.java" -o -name "*.yml" -o -name "*.xml" \) 2>/dev/null | \
        sort | xargs cat | sha256sum | cut -d' ' -f1
}

detect_changes() {
    log_info "Detecting changes in project..."
    
    local current_hash
    current_hash=$(calculate_project_hash)
    
    local previous_hash=""
    if [[ -f "$CHANGE_CACHE_FILE" ]]; then
        previous_hash=$(cat "$CHANGE_CACHE_FILE")
    fi
    
    # Save current hash
    echo "$current_hash" > "$CHANGE_CACHE_FILE"
    
    if [[ "$current_hash" != "$previous_hash" ]]; then
        log_info "Changes detected in project"
        return 0
    else
        log_info "No changes detected"
        return 1
    fi
}

build_plugin() {
    log_info "Building plugin with Maven..."
    
    cd "$PROJECT_DIR"
    
    # Clean and build the plugin
    mvn clean package -q || {
        log_error "Maven build failed"
        exit 1
    }
    
    log_success "Plugin built successfully"
}

remove_old_plugin() {
    log_info "Removing old plugin versions from $PLUGINS_DIR..."
    
    if [[ ! -d "$PLUGINS_DIR" ]]; then
        log_warning "Plugins directory does not exist: $PLUGINS_DIR"
        log_info "Creating plugins directory..."
        mkdir -p "$PLUGINS_DIR"
        return
    fi
    
    local removed_count=0
    for file in "$PLUGINS_DIR"/${PLUGIN_NAME}*.jar; do
        if [[ -f "$file" ]]; then
            log_info "Removing old plugin: $(basename "$file")"
            rm -f "$file"
            removed_count=$((removed_count + 1))
        fi
    done
    
    if [[ $removed_count -eq 0 ]]; then
        log_info "No old plugin versions found to remove"
    else
        log_info "Removed $removed_count old plugin file(s)"
    fi
}

deploy_plugin() {
    local version="$1"
    
    log_info "Deploying plugin to $PLUGINS_DIR..."
    
    mkdir -p "$PLUGINS_DIR"
    
    local jar_file="$PROJECT_DIR/target/${PLUGIN_ARTIFACT_ID}-${version}.jar"
    
    if [[ -f "$jar_file" ]]; then
        cp "$jar_file" "$PLUGINS_DIR/" || {
            log_error "Failed to copy plugin to $PLUGINS_DIR"
            exit 1
        }
        log_info "Deployed: ${PLUGIN_ARTIFACT_ID}-${version}.jar"
        log_success "Successfully deployed plugin to $PLUGINS_DIR"
    else
        log_error "JAR file not found: $jar_file"
        exit 1
    fi
}

save_version_cache() {
    local version="$1"
    echo "$version" > "$VERSION_CACHE_FILE"
    log_info "Saved version $version to cache"
}

load_version_cache() {
    if [[ -f "$VERSION_CACHE_FILE" ]]; then
        cat "$VERSION_CACHE_FILE"
    else
        echo ""
    fi
}

show_usage() {
    cat << EOF
Usage: $0 [OPTIONS]

Build and deploy the FriendlyDeathChest plugin with intelligent change detection.

OPTIONS:
    -t, --type TYPE     Version increment type: major, minor, patch (default: patch)
    -v, --version VER   Set specific version instead of incrementing
    -n, --no-increment  Build and deploy without incrementing version
    -f, --force         Force build even if no changes detected
    -c, --check-only    Only check for changes, don't build or deploy
    -b, --build-only    Build plugin but don't deploy to server
    -h, --help          Show this help message

EXAMPLES:
    $0                  # Auto-detect changes and increment patch version if needed
    $0 -t minor         # Force minor version increment and build
    $0 -v 2.1.0         # Set version to 2.1.0 and build
    $0 -f               # Force build even without changes
    $0 -c               # Check for changes only
    $0 -b               # Build plugin but don't deploy
    $0 -b -f            # Force build without deployment

CONFIGURATION:
    Plugin name: $PLUGIN_NAME
    Plugins directory: $PLUGINS_DIR
    Project directory: $PROJECT_DIR

CHANGE DETECTION:
    The script automatically detects changes in source files and configurations.
    Only builds and increments version when changes are detected (unless forced).
EOF
}

# --- Main Execution ---
main() {
    local version_type="patch"
    local specific_version=""
    local no_increment=false
    local force_build=false
    local check_only=false
    local build_only=false
    
    # Parse command line arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            -t|--type)
                version_type="$2"
                if [[ ! "$version_type" =~ ^(major|minor|patch)$ ]]; then
                    log_error "Invalid version type: $version_type (must be: major, minor, patch)"
                    exit 1
                fi
                shift 2
                ;;
            -v|--version)
                specific_version="$2"
                shift 2
                ;;
            -n|--no-increment)
                no_increment=true
                shift
                ;;
            -f|--force)
                force_build=true
                shift
                ;;
            -c|--check-only)
                check_only=true
                shift
                ;;
            -b|--build-only)
                build_only=true
                shift
                ;;
            -h|--help)
                show_usage
                exit 0
                ;;
            *)
                log_error "Unknown option: $1"
                show_usage
                exit 1
                ;;
        esac
    done
    
    log_info "Starting FriendlyDeathChest plugin build and deployment..."
    
    # Check dependencies
    check_dependencies
    
    # Detect changes
    local changes_detected=false
    if detect_changes; then
        changes_detected=true
    fi
    
    # Check-only mode
    if [[ "$check_only" == true ]]; then
        if [[ "$changes_detected" == true ]]; then
            log_info "Changes detected in project"
            exit 0
        else
            log_info "No changes detected"
            exit 1
        fi
    fi
    
    # Determine if we should build
    local should_build=false
    if [[ "$force_build" == true ]]; then
        log_info "Force build requested"
        should_build=true
    elif [[ "$changes_detected" == true ]]; then
        log_info "Changes detected, build required"
        should_build=true
    elif [[ -n "$specific_version" ]]; then
        log_info "Specific version requested, build required"
        should_build=true
    elif [[ "$no_increment" == false ]]; then
        log_info "Version increment requested, build required"
        should_build=true
    else
        log_info "No changes detected and no build forced"
        log_info "Use -f/--force to build anyway"
        exit 0
    fi
    
    if [[ "$should_build" == false ]]; then
        log_info "No build required"
        exit 0
    fi
    
    # Get current version
    local current_version
    current_version=$(get_current_version)
    log_info "Current version: $current_version"
    
    # Determine new version
    local new_version="$current_version"
    if [[ -n "$specific_version" ]]; then
        new_version="$specific_version"
        log_info "Setting version to: $new_version"
    elif [[ "$no_increment" == false ]]; then
        new_version=$(increment_version "$current_version" "$version_type")
        log_info "Incrementing $version_type version: $current_version -> $new_version"
    else
        log_info "Using current version: $new_version"
    fi
    
    # Update version if changed
    if [[ "$new_version" != "$current_version" ]]; then
        update_version "$new_version"
    fi
    
    # Build the plugin
    build_plugin
    
    # Save version to cache
    save_version_cache "$new_version"
    
    if [[ "$build_only" == true ]]; then
        log_success "Build completed successfully!"
        log_success "Version: $new_version"
        
        # Show built file
        local jar_file="$PROJECT_DIR/target/${PLUGIN_ARTIFACT_ID}-${new_version}.jar"
        echo
        echo "Built file:"
        if [[ -f "$jar_file" ]]; then
            echo "  ✓ $(basename "$jar_file") ($(du -h "$jar_file" | cut -f1))"
        fi
        
        echo
        echo "Next steps:"
        echo "1. Run without -b/--build-only to deploy to server"
        echo "2. Or manually copy JAR file from target/ directory"
        
        if [[ "$changes_detected" == true ]]; then
            echo "3. Changes were detected in the project"
        fi
    else
        # Remove old plugin version
        remove_old_plugin
        
        # Deploy new plugin
        deploy_plugin "$new_version"
        
        log_success "Build and deployment completed successfully!"
        log_success "Version: $new_version"
        log_success "Location: $PLUGINS_DIR/"
        
        # Show deployed file
        echo
        echo "Deployed file:"
        local jar_file="$PLUGINS_DIR/${PLUGIN_ARTIFACT_ID}-${new_version}.jar"
        if [[ -f "$jar_file" ]]; then
            echo "  ✓ $(basename "$jar_file")"
        fi
        
        # Show next steps
        echo
        echo "Next steps:"
        echo "1. Restart your Minecraft server to load the new plugin version"
        echo "2. Test the /fdc command to verify the plugin is loaded"
        echo "3. Check server logs for any errors during plugin loading"
        
        if [[ "$changes_detected" == true ]]; then
            echo "4. Changes were detected and deployed"
        fi
    fi
}

# Run main function with all arguments
main "$@" 