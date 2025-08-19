package com.mocharealm.accompanist.lyrics.parser

import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.parser.TTMLParser
import com.mocharealm.accompanist.lyrics.core.utils.parseAsTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TTMLParserTest {
    @Test
    fun testMeParseResult() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
                <head><metadata xmlns="">
                    <ttm:agent type="singer" xml:id="v1"/>
                    <ttm:agent type="person" xml:id="v2"/>
                </metadata></head>
                <body>
                    <div xmlns="">
                        <p begin="00:00.130" end="00:02.820" ttm:agent="v1">
                            <span begin="00:00.130" end="00:00.230">I</span> <span begin="00:00.230" end="00:00.450">promise</span> <span begin="00:00.450" end="00:00.620">that</span>
                            <span ttm:role="x-translation" xml:lang="zh-CN">确信我就是这世间的独一无二</span>
                        </p>
                        <p begin="01:14.650" end="01:17.289" ttm:agent="v2">
                            <span begin="01:14.650" end="01:14.780">I</span> <span begin="01:14.780" end="01:15.010">never</span>
                            <span ttm:role="x-bg" begin="01:15.010" end="01:15.420">
                                <span begin="01:15.010" end="01:15.250">wanna</span> <span begin="01:15.250" end="01:15.420">see</span>
                                <span ttm:role="x-translation" xml:lang="zh-CN">看见过</span>
                            </span>
                            <span ttm:role="x-translation" xml:lang="zh-CN">我从未</span>
                        </p>
                    </div>
                </body>
            </tt>
        """.trimIndent()

        // 修改点: 直接传递TTML字符串
        val result = TTMLParser.parse(ttml)

        // 验证解析出的行数 (1行主歌词, 1行主歌词, 1行背景和声)
        assertEquals(3, result.lines.size)

        // 行按时间排序
        val firstLine = result.lines[0] as KaraokeLine
        val secondLine = result.lines[1] as KaraokeLine
        val bgLine = result.lines[2] as KaraokeLine

        // 验证第一行的对齐方式(第一个agent v1)应该是左对齐
        assertEquals(KaraokeAlignment.Start, firstLine.alignment)

        // 验证第二行的对齐方式(第二个agent v2)应该是右对齐
        assertEquals(KaraokeAlignment.End, secondLine.alignment)

        // 验证背景音节的对齐方式应该继承自父元素的agent(v2 右对齐)
        assertEquals(KaraokeAlignment.End, bgLine.alignment)

        // 验证第一行翻译
        assertEquals("确信我就是这世间的独一无二", firstLine.translation?.trim())

        // 新增验证: 检查第一行音节拼接后是否包含正确的空格
        assertEquals("I promise that", firstLine.syllables.joinToString("") { it.content }.trim())

        // 验证第二行（主歌词）翻译
        assertEquals("我从未", secondLine.translation?.trim())

        // 新增验证: 检查第二行音节拼接
        assertEquals("I never", secondLine.syllables.joinToString("") { it.content }.trim())

        // 验证背景音节被正确解析
        assertTrue(bgLine.isAccompaniment)
        assertEquals(2, bgLine.syllables.size) // "wanna", "see"

        // 新增验证: 检查背景音节拼接后的文本，注意末尾的空格已被我们的逻辑去除
        assertEquals("wanna see", bgLine.syllables.joinToString("") { it.content }.trim())

        // 验证背景音节包含其独立的翻译
        assertEquals("看见过", bgLine.translation?.trim())
    }

    @Test
    fun testNestedTranslationAndSpacing() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata"><body><div>
            <p begin="00:00.100" end="00:03.000">
                <span begin="00:00.100" end="00:00.500">Main</span> <span begin="00:00.500" end="00:01.000">vocals</span>
                <span ttm:role="x-bg" begin="00:01.500" end="00:02.500">
                    <span begin="00:01.500" end="00:02.000">background</span><span begin="00:02.000" end="00:02.500">harmony</span>
                    <span ttm:role="x-translation" xml:lang="zh-CN">背景和声</span>
                </span>
                <span ttm:role="x-translation" xml:lang="zh-CN">主歌声</span>
            </p>
            </div></body></tt>
            """.trimIndent()

        val result = TTMLParser.parse(ttml)

        assertEquals(2, result.lines.size)

        val mainLine = result.lines.find { !(it as KaraokeLine).isAccompaniment } as? KaraokeLine
        val bgLine = result.lines.find { (it as KaraokeLine).isAccompaniment } as? KaraokeLine

        assertNotNull(mainLine)
        assertNotNull(bgLine)

        // 主歌词行应该有p级别的翻译
        assertEquals("主歌声", mainLine.translation)
        // 新增验证: "Main" 和 "vocals" 之间有空格
        assertEquals("Main vocals", mainLine.syllables.joinToString("") { it.content }.trim())

        // 背景音节行应该有自己的翻译
        assertEquals("背景和声", bgLine.translation)
        // "background" 和 "harmony" 之间没有空格
        assertEquals("backgroundharmony", bgLine.syllables.joinToString("") { it.content }.trim())
    }

    @Test
    fun testBgWithoutSpanStartAndEnd() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata"><body><div>
            <p begin="00:00.100" end="00:03.000">
                <span begin="00:00.100" end="00:00.500">Main</span> <span begin="00:00.500" end="00:01.000">vocals</span>
                <span ttm:role="x-bg">
                    <span begin="00:01.500" end="00:02.000">background</span> <span begin="00:02.000" end="00:02.500">harmony</span>
                    <span ttm:role="x-translation" xml:lang="zh-CN">背景和声</span>
                </span>
                <span ttm:role="x-translation" xml:lang="zh-CN">主歌声</span>
            </p>
            </div></body></tt>
            """.trimIndent()

        val result = TTMLParser.parse(ttml)

        assertEquals(2, result.lines.size)

        val mainLine = result.lines.find { !(it as KaraokeLine).isAccompaniment } as? KaraokeLine
        val bgLine = result.lines.find { (it as KaraokeLine).isAccompaniment } as? KaraokeLine

        assertNotNull(mainLine)
        assertNotNull(bgLine)

        assertTrue(bgLine.isAccompaniment)
        assertEquals("00:01.500".parseAsTime(),bgLine.start)
        assertEquals("00:02.500".parseAsTime(),bgLine.end)
    }

    @Test
    fun testITunesMetadataTranslation() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
                xmlns:ttm="http://www.w3.org/ns/ttml#metadata" itunes:timing="Word" xml:lang="en">
                <head>
                    <metadata>
                        <ttm:agent type="person" xml:id="v1" />
                        <iTunesMetadata xmlns="http://music.apple.com/lyric-ttml-internal">
                            <translations>
                                <translation type="subtitle" xml:lang="zh-Hans">
                                    <text for="L1">那是两个相爱的人</text>
                                    <text for="L2">坐在车里 听着《Blonde》 心悄悄靠近彼此</text>
                                    <text for="L3">粉橙色的天空下 如孩童般纯真 无关 Donald Glover</text>
                                </translation>
                            </translations>
                        </iTunesMetadata>
                    </metadata>
                </head>
                <body dur="3:29.260">
                    <div begin="15.465" end="30.064">
                        <p begin="15.465" end="16.533" itunes:key="L1" ttm:agent="v1">
                            <span begin="15.465" end="15.694">It</span>
                            <span begin="15.694" end="15.894">was</span>
                            <span begin="15.894" end="16.055">just</span>
                            <span begin="16.055" end="16.155">two</span>
                            <span begin="16.155" end="16.533">lovers</span>
                        </p>
                        <p begin="16.534" end="17.000" itunes:key="L2" ttm:agent="v1">
                            <span begin="16.534" end="17.000">Sitting in the car</span>
                        </p>
                        <p begin="17.001" end="18.000" ttm:agent="v1">
                            <span begin="17.001" end="18.000">No translation here</span>
                        </p>
                        <p begin="18.001" end="19.000" itunes:key="L3" ttm:agent="v1">
                            <span begin="18.001" end="19.000">Under the orange sky</span>
                            <span ttm:role="x-translation" xml:lang="zh-CN">行内优先</span>
                        </p>
                    </div>
                </body>
            </tt>
        """.trimIndent()

        val result = TTMLParser.parse(ttml)
        assertEquals(4, result.lines.size)

        val line1 = result.lines[0] as KaraokeLine
        val line2 = result.lines[1] as KaraokeLine
        val line3 = result.lines[2] as KaraokeLine
        val line4 = result.lines[3] as KaraokeLine

        // 通过itunes:key获取翻译
        assertEquals("那是两个相爱的人", line1.translation)
        assertEquals("坐在车里 听着《Blonde》 心悄悄靠近彼此", line2.translation)
        // 没有key也没有行内翻译，translation应为null
        assertEquals(null, line3.translation)
        // 行内翻译优先
        assertEquals("行内优先", line4.translation)
    }

    @Test
    fun testITunesMetadataBgTranslation() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
                xmlns:ttm="http://www.w3.org/ns/ttml#metadata" itunes:timing="Word" xml:lang="en">
                <head>
                    <metadata>
                        <ttm:agent type="person" xml:id="v1" />
                        <iTunesMetadata xmlns="http://music.apple.com/lyric-ttml-internal">
                            <translations>
                                <translation type="subtitle" xml:lang="zh-Hans">
                                    <text for="L57">宝贝 这就是我的迷人之处（宝贝 这就是我的迷）</text>
                                </translation>
                            </translations>
                        </iTunesMetadata>
                    </metadata>
                </head>
                <body dur="3:29.260">
                    <div begin="2:25.240" end="2:28.737">
                        <p begin="2:25.240" end="2:28.737" itunes:key="L57" ttm:agent="v1000">
                            <span begin="2:25.240" end="2:25.719">Baby,</span>
                            <span begin="2:25.719" end="2:25.887">that's</span>
                            <span begin="2:25.887" end="2:26.052">the</span>
                            <span begin="2:26.052" end="2:26.236">fun</span>
                            <span begin="2:26.236" end="2:26.404">of</span>
                            <span begin="2:26.404" end="2:26.704">me</span>
                            <span ttm:role="x-bg">
                                <span begin="2:26.545" end="2:26.970">(Baby,</span>
                                <span begin="2:26.970" end="2:27.154">that's</span>
                                <span begin="2:27.154" end="2:27.420">the</span>
                                <span begin="2:27.420" end="2:27.691">fun</span>
                                <span begin="2:27.691" end="2:27.873">of</span>
                                <span begin="2:27.873" end="2:28.737">me)</span>
                            </span>
                        </p>
                    </div>
                </body>
            </tt>
        """.trimIndent()

        val result = TTMLParser.parse(ttml)
        // 应有两行：主歌词和和声
        assertEquals(2, result.lines.size)
        val mainLine = result.lines.find { !(it as KaraokeLine).isAccompaniment } as KaraokeLine
        val bgLine = result.lines.find { (it as KaraokeLine).isAccompaniment } as KaraokeLine
        // 主歌词和和声都应能通过父key获取翻译
        assertEquals("宝贝 这就是我的迷人之处", mainLine.translation) //括号外内容
        assertEquals("宝贝 这就是我的迷", bgLine.translation) //括号内内容
    }
}