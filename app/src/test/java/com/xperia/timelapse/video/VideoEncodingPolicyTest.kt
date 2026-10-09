package com.xperia.timelapse.video
import org.junit.Assert.*
import org.junit.Test
class VideoEncodingPolicyTest {
 private val all=VideoEncoding.values().toSet()
 @Test fun automaticPrefersHevc() {assertEquals(listOf(VideoEncoding.HEVC,VideoEncoding.AVC),VideoEncodingPolicy.candidates(VideoPreference.AUTO,all))}
 @Test fun hevcFallsBackWhenUnavailable() {assertEquals(listOf(VideoEncoding.AVC),VideoEncodingPolicy.candidates(VideoPreference.HEVC,setOf(VideoEncoding.AVC)))}
 @Test fun av1FallbackOrder() {assertEquals(listOf(VideoEncoding.AV1,VideoEncoding.HEVC,VideoEncoding.AVC),VideoEncodingPolicy.candidates(VideoPreference.AV1,all))}
 @Test fun explicitAvcRemainsAvc() {assertEquals(listOf(VideoEncoding.AVC),VideoEncodingPolicy.candidates(VideoPreference.AVC,all))}
 @Test fun unavailableCodecsNeverSelected() {assertTrue(VideoEncodingPolicy.candidates(VideoPreference.AUTO,emptySet()).isEmpty())}
 @Test fun savingPresetHasLowerBitrate() {all.forEach { assertTrue(VideoEncodingPolicy.bitrate(it,VideoQuality.SMALL)<VideoEncodingPolicy.bitrate(it,VideoQuality.STANDARD));assertTrue(VideoEncodingPolicy.bitrate(it,VideoQuality.STANDARD)<VideoEncodingPolicy.bitrate(it,VideoQuality.HIGH)) }}
 @Test fun standardRatesMatchDefaults() {assertEquals(4_000_000,VideoEncodingPolicy.bitrate(VideoEncoding.HEVC,VideoQuality.STANDARD));assertEquals(8_000_000,VideoEncodingPolicy.bitrate(VideoEncoding.AVC,VideoQuality.STANDARD));assertEquals(3_000_000,VideoEncodingPolicy.bitrate(VideoEncoding.AV1,VideoQuality.STANDARD))}
}
