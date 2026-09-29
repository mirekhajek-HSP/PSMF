#!/bin/sh
# Compares the required-reason API symbols in a BUILT app binary with the
# categories declared in PrivacyInfo.xcprivacy. Exits 1 if they disagree
# either way. Apple's upload check (ITMS-91053) is symbol-based, so this one
# is too.
#
# Usage: iosApp/scripts/check-required-reason-apis.sh <path/to/iosApp.app>
#
# Check the RELEASE build: it is what gets uploaded. A Debug build keeps the
# real code in iosApp.debug.dylib, which this script also reads if present.
#
# The symbol lists are Apple's, from the NSPrivacyAccessedAPIType reference.
# getattrlist and its variants appear in both the file timestamp and disk
# space lists, and count for both.
set -eu

app=${1:?usage: $0 <path/to/iosApp.app>}
manifest="$app/PrivacyInfo.xcprivacy"
[ -f "$manifest" ] || { echo "FAIL: no PrivacyInfo.xcprivacy in $app"; exit 1; }

imports=$(for b in "$app/iosApp" "$app/iosApp.debug.dylib"; do
  [ -f "$b" ] && nm -u "$b"
done | sort -u)
selectors=$(for b in "$app/iosApp" "$app/iosApp.debug.dylib"; do
  [ -f "$b" ] && otool -v -s __TEXT __objc_methname "$b" | tr -s ' \t' '\n'
done | sort -u)

# category|C functions and Foundation symbols (as imports)|ObjC selectors
categories='FileTimestamp|stat fstat fstatat lstat getattrlist getattrlistbulk fgetattrlist getattrlistat NSFileCreationDate NSFileModificationDate NSURLContentModificationDateKey NSURLCreationDateKey|fileModificationDate
SystemBootTime|mach_absolute_time|systemUptime
DiskSpace|statfs statvfs fstatfs fstatvfs getattrlist fgetattrlist getattrlistat NSURLVolumeAvailableCapacityKey NSURLVolumeAvailableCapacityForImportantUsageKey NSURLVolumeAvailableCapacityForOpportunisticUsageKey NSURLVolumeTotalCapacityKey NSFileSystemFreeSize NSFileSystemSize|
ActiveKeyboards||activeInputModes
UserDefaults|OBJC_CLASS_$_NSUserDefaults|'

declared=$(plutil -extract NSPrivacyAccessedAPITypes xml1 -o - "$manifest" \
  | sed -n 's#.*<string>NSPrivacyAccessedAPICategory\(.*\)</string>.*#\1#p')

status=0
echo "$categories" | while IFS='|' read -r category symbols sels; do
  found=""
  for s in $symbols; do
    printf '%s\n' "$imports" | grep -qxF "_$s" && found="$found $s"
  done
  for s in $sels; do
    printf '%s\n' "$selectors" | grep -qxF "$s" && found="$found $s"
  done
  if printf '%s\n' "$declared" | grep -qxF "$category"; then
    is_declared=yes
  else
    is_declared=no
  fi
  if [ -n "$found" ] && [ $is_declared = no ]; then
    echo "FAIL  $category: used but NOT declared:$found"; exit 1
  elif [ -z "$found" ] && [ $is_declared = yes ]; then
    echo "FAIL  $category: declared but no symbol present"; exit 1
  elif [ -n "$found" ]; then
    echo "ok    $category: declared, present:$found"
  else
    echo "ok    $category: not used, not declared"
  fi
done || status=1

exit $status
