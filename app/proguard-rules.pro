# Project-specific R8/ProGuard rules.
# Minification is disabled for the current scaffold; rules will be added when a
# release build is enabled.

# Keep transport implementations that are instantiated reflectively in the future.
-keep class com.nexysa7.crypto.data.transport.** { *; }
