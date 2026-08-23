package com.github.mwiest.voclet.data.ai

/**
 * Which AI backend the user prefers for translation hints and camera import.
 *
 * - [AUTO]: use the on-device model when one is downloaded, otherwise fall back
 *   to the cloud service.
 * - [CLOUD]: always use the user-configured cloud service (see CloudProvider).
 * - [LOCAL]: only use the on-device model (no AI if none is downloaded).
 */
enum class AiBackend { AUTO, CLOUD, LOCAL }
