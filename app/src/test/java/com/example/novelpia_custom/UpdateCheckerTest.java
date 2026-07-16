package com.example.novelpia_custom;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * UpdateChecker 핵심 로직 단위 테스트
 * - parseVersion: "1.3.5" → int[]
 * - isNewer: SemVer 비교
 * - check: GitHub API 호출 제외 로직 검증
 */
public class UpdateCheckerTest {

    // ─── parseVersion ─────────────────────────────────────

    @Test
    public void parseVersion_정상_3자리() {
        assertArrayEquals(new int[]{1, 3, 5}, UpdateChecker.parseVersion("1.3.5"));
    }

    @Test
    public void parseVersion_정상_2자리() {
        assertArrayEquals(new int[]{2, 0}, UpdateChecker.parseVersion("2.0"));
    }

    @Test
    public void parseVersion_정상_1자리() {
        assertArrayEquals(new int[]{5}, UpdateChecker.parseVersion("5"));
    }

    @Test
    public void parseVersion_정상_4자리() {
        assertArrayEquals(new int[]{1, 2, 3, 4}, UpdateChecker.parseVersion("1.2.3.4"));
    }

    @Test
    public void parseVersion_null_빈배열() {
        assertArrayEquals(new int[0], UpdateChecker.parseVersion(null));
    }

    @Test
    public void parseVersion_빈문자열_빈배열() {
        assertArrayEquals(new int[0], UpdateChecker.parseVersion(""));
    }

    @Test
    public void parseVersion_숫자아님_빈배열() {
        assertArrayEquals(new int[0], UpdateChecker.parseVersion("abc"));
    }

    @Test
    public void parseVersion_혼합_빈배열() {
        assertArrayEquals(new int[0], UpdateChecker.parseVersion("1.2.abc"));
    }

    // ─── isNewer ───────────────────────────────────────────

    @Test
    public void isNewer_패치업데이트_true() {
        assertTrue("1.3.5 → 1.3.6", UpdateChecker.isNewer("1.3.5", "1.3.6"));
    }

    @Test
    public void isNewer_마이너업데이트_true() {
        assertTrue("1.3.5 → 1.4.0", UpdateChecker.isNewer("1.3.5", "1.4.0"));
    }

    @Test
    public void isNewer_메이저업데이트_true() {
        assertTrue("1.3.5 → 2.0", UpdateChecker.isNewer("1.3.5", "2.0"));
    }

    @Test
    public void isNewer_동일_false() {
        assertFalse("1.3.5 == 1.3.5", UpdateChecker.isNewer("1.3.5", "1.3.5"));
    }

    @Test
    public void isNewer_현재가최신_false() {
        assertFalse("1.3.6 > 1.3.5", UpdateChecker.isNewer("1.3.6", "1.3.5"));
    }

    @Test
    public void isNewer_현재가최신메이저_false() {
        assertFalse("2.0 > 1.9.9", UpdateChecker.isNewer("2.0", "1.9.9"));
    }

    @Test
    public void isNewer_세그먼트차이_업데이트() {
        assertTrue("1.3.5 → 1.3.5.1", UpdateChecker.isNewer("1.3.5", "1.3.5.1"));
    }

    @Test
    public void isNewer_세그먼트차이_현재많음_false() {
        assertFalse("1.3.5.1 > 1.3.5", UpdateChecker.isNewer("1.3.5.1", "1.3.5"));
    }

    @Test
    public void isNewer_현재null_false() {
        assertFalse(UpdateChecker.isNewer(null, "1.3.6"));
    }

    @Test
    public void isNewer_최신null_false() {
        assertFalse(UpdateChecker.isNewer("1.3.5", null));
    }

    @Test
    public void isNewer_둘다null_false() {
        assertFalse(UpdateChecker.isNewer(null, null));
    }

    @Test
    public void isNewer_현재빈문자열_false() {
        assertFalse(UpdateChecker.isNewer("", "1.3.6"));
    }

    @Test
    public void isNewer_현재형식오류_false() {
        assertFalse(UpdateChecker.isNewer("abc", "1.3.6"));
    }

    // ─── check 로직 검증 (API 호출 제외) ─────────────────

    @Test
    public void UpdateInfo_none_hasUpdate_false() {
        UpdateChecker.UpdateInfo info = UpdateChecker.UpdateInfo.none();
        assertFalse(info.hasUpdate);
    }

    @Test
    public void UpdateInfo_생성_필드정상() {
        UpdateChecker.UpdateInfo info = new UpdateChecker.UpdateInfo(
                true, "1.3.6", "https://example.com/apk", 5000000, "릴리스 노트"
        );
        assertTrue(info.hasUpdate);
        assertEquals("1.3.6", info.latestVersion);
        assertEquals("https://example.com/apk", info.downloadUrl);
        assertEquals(5000000, info.apkSize);
        assertEquals("릴리스 노트", info.releaseBody);
    }
}