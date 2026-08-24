package com.github.mwiest.voclet.data.ai

/**
 * One logcat tag for the whole AI path - routing decision, cloud request and
 * response, on-device inference - so a session can be followed with:
 *
 * ```
 * adb logcat -s VocletAi
 * ```
 *
 * Never log the API key, the request body or the raw response: the key is a
 * credential, and the body carries the user's vocabulary and photos. Endpoint,
 * model, HTTP status and the provider's error message are enough to tell a bad
 * key from a bad model ID from a network failure.
 */
const val AI_LOG_TAG = "VocletAi"
