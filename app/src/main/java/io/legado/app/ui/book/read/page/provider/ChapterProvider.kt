package io.legado.app.ui.book.read.page.provider

import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import io.legado.app.App
import io.legado.app.constant.AppPattern
import io.legado.app.constant.EventBus
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.AppConfig
import io.legado.app.help.BookHelp
import io.legado.app.help.ReadBookConfig
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.ui.book.read.page.entities.TextChar
import io.legado.app.ui.book.read.page.entities.TextLine
import io.legado.app.ui.book.read.page.entities.TextPage
import io.legado.app.utils.*
import java.util.*
import kotlin.collections.HashMap


@Suppress("DEPRECATION")
object ChapterProvider {
    @JvmStatic
    private var viewWidth = 0

    @JvmStatic
    private var viewHeight = 0

    @JvmStatic
    var paddingLeft = 0

    @JvmStatic
    var paddingTop = 0

    @JvmStatic
    var visibleWidth = 0

    @JvmStatic
    var visibleHeight = 0

    @JvmStatic
    var visibleRight = 0

    @JvmStatic
    var visibleBottom = 0

    @JvmStatic
    private var lineSpacingExtra = 0

    @JvmStatic
    private var paragraphSpacing = 0

    @JvmStatic
    private var titleTopSpacing = 0

    @JvmStatic
    private var titleBottomSpacing = 0

    @JvmStatic
    var typeface: Typeface = Typeface.SANS_SERIF

    @JvmStatic
    lateinit var titlePaint: TextPaint

    @JvmStatic
    lateinit var contentPaint: TextPaint

    init {
        upStyle()
    }

    /**
     * 获取拆分完的章节数据
     */
    fun getTextChapter(
        book: Book,
        bookChapter: BookChapter,
        contents: List<String>,
        chapterSize: Int,
        imageStyle: String?,
    ): TextChapter {
        val textPages = arrayListOf<TextPage>()
        val stringBuilder = StringBuilder()
        var durY = 0f
        var paint = Pair(titlePaint, contentPaint)
        BookHelp.getFontPath(book, bookChapter)?.let {
            val typeface = getTypeface(it)
            paint = getPaint(typeface)
        }
        textPages.add(TextPage())
        contents.forEachIndexed { index, text ->
            val matcher = AppPattern.imgPattern.matcher(text)
            if (matcher.find()) {
                var src = matcher.group(1)
                if (!book.isEpub()) {
                    src = NetworkUtils.getAbsoluteURL(bookChapter.url, src)
                }
                src?.let {
                    durY = setTypeImage(
                        book, bookChapter, src, durY, textPages, imageStyle
                    )
                }
            } else {
                val isTitle = index == 0
                val textPaint = if (isTitle) paint.first else paint.second
                if (!(isTitle && ReadBookConfig.titleMode == 2)) {
                    durY = setTypeText(text, durY, textPages, stringBuilder, isTitle, textPaint)
                }
            }
        }
        textPages.last().height = durY + 20.dp
        textPages.last().text = stringBuilder.toString()
        textPages.forEachIndexed { index, item ->
            item.index = index
            item.pageSize = textPages.size
            item.chapterIndex = bookChapter.index
            item.chapterSize = chapterSize
            item.title = bookChapter.title
            item.upLinesPosition()
        }

        return TextChapter(
            bookChapter.index, bookChapter.title,
            bookChapter.getAbsoluteURL().substringBefore(","),
            textPages, chapterSize, paint.first, paint.second
        )
    }

    private fun setTypeImage(
        book: Book,
        chapter: BookChapter,
        src: String,
        y: Float,
        textPages: ArrayList<TextPage>,
        imageStyle: String?,
    ): Float {
        var durY = y
        ImageProvider.getImage(book, chapter.index, src)?.let {
            if (durY > visibleHeight) {
                textPages.last().height = durY
                textPages.add(TextPage())
                durY = 0f
            }
            var height = it.height
            var width = it.width
            when (imageStyle?.toUpperCase(Locale.ROOT)) {
                "FULL" -> {
                    width = visibleWidth
                    height = it.height * visibleWidth / it.width
                }
                else -> {
                    if (it.width > visibleWidth) {
                        height = it.height * visibleWidth / it.width
                        width = visibleWidth
                    }
                    if (height > visibleHeight) {
                        width = width * visibleHeight / height
                        height = visibleHeight
                    }
                    if (durY + height > visibleHeight) {
                        textPages.last().height = durY
                        textPages.add(TextPage())
                        durY = 0f
                    }
                }
            }
            val textLine = TextLine(isImage = true)
            textLine.lineTop = durY
            durY += height
            textLine.lineBottom = durY
            val (start, end) = if (visibleWidth > width) {
                val adjustWidth = (visibleWidth - width) / 2f
                Pair(
                    paddingLeft.toFloat() + adjustWidth,
                    paddingLeft.toFloat() + adjustWidth + width
                )
            } else {
                Pair(paddingLeft.toFloat(), (paddingLeft + width).toFloat())
            }
            textLine.textChars.add(
                TextChar(
                    charData = src,
                    start = start,
                    end = end,
                    isImage = true
                )
            )
            textPages.last().textLines.add(textLine)
        }
        return durY + paragraphSpacing / 10f
    }

    /**
     * 排版文字
     */
    //todo
    private fun setTypeText(
        text: String,
        y: Float,
        textPages: ArrayList<TextPage>,
        stringBuilder: StringBuilder,
        isTitle: Boolean,
        textPaint: TextPaint
    ): Float {
        var durY = if (isTitle) y + titleTopSpacing else y
        /*
        val layout = StaticLayout(
            text, textPaint, visibleWidth, Layout.Alignment.ALIGN_NORMAL, 0f, 0f, true
        )*/
        val layout = TextProcess(text,textPaint)
        //Log.d("debug2","宽度:$visibleWidth 行数:${layout.lineCount}")
        for (lineIndex in 0 until layout.lineCount) {
            val textLine = TextLine(isTitle = isTitle)
            val words =
                text.substring(layout.getLineStart(lineIndex), layout.getLineEnd(lineIndex))
            val desiredWidth = layout.getLineWidth(lineIndex)
            var isLastLine = false
            val lineCompressMod = layout.getLineCompressMod(lineIndex)
            //Log.d("debug1","第 $lineIndex 行 行起始：${layout.getLineStart(lineIndex)} 行结束：${layout.getLineEnd(lineIndex)} 行宽${layout.getLineWidth(lineIndex)} 行内容：$words")
            if (lineIndex == 0 && layout.lineCount > 1 && !isTitle) {
                //第一行
                textLine.text = words
                addCharsToLineFirst(textLine, words.toStringArray(), textPaint, desiredWidth ,lineCompressMod)
            } else if (lineIndex == layout.lineCount - 1) {
                //最后一行
                textLine.text = "$words\n"
                isLastLine = true
                val x = if (isTitle && ReadBookConfig.titleMode == 1)
                    (visibleWidth - layout.getLineWidth(lineIndex)) / 2
                else 0f
                addCharsToLineLast(textLine, words.toStringArray(), textPaint, x,lineCompressMod)
            } else {
                //中间行
                textLine.text = words
                addCharsToLineMiddle(textLine, words.toStringArray(), textPaint, desiredWidth, 0f,lineCompressMod)
            }
            if (durY + textPaint.textHeight > visibleHeight) {
                //当前页面结束,设置各种值
                textPages.last().text = stringBuilder.toString()
                textPages.last().height = durY
                //新建页面
                textPages.add(TextPage())
                stringBuilder.clear()
                durY = 0f
            }
            stringBuilder.append(words)
            if (isLastLine) stringBuilder.append("\n")
            textPages.last().textLines.add(textLine)
            textLine.upTopBottom(durY, textPaint)
            durY += textPaint.textHeight * lineSpacingExtra / 10f
            textPages.last().height = durY
        }
        if (isTitle) durY += titleBottomSpacing
        durY += textPaint.textHeight * paragraphSpacing / 10f
        return durY
    }

    /**
     * 有缩进,两端对齐
     */
    private fun addCharsToLineFirst(
        textLine: TextLine,
        words: Array<String>,
        textPaint: TextPaint,
        desiredWidth: Float,
        lineCompressMod:Int,
    ) {
        var x = 0f
        if (!ReadBookConfig.textFullJustify) {
            addCharsToLineLast(textLine, words, textPaint, x,lineCompressMod)
            return
        }
        val bodyIndent = ReadBookConfig.paragraphIndent
        val icw = StaticLayout.getDesiredWidth(bodyIndent, textPaint) / bodyIndent.length
        var num = 0
        //Log.d("debug2","|$bodyIndent| ${bodyIndent.length} icw is $icw")
        bodyIndent.toStringArray().forEach {
            val x1 = x + icw
            //Log.d("debug2","$num")
            num++
            textLine.addTextChar(charData = it, start = paddingLeft + x, end = paddingLeft + x1)
            x = x1
        }
        val words1 = words.copyOfRange(bodyIndent.length, words.size)
        //Log.d("debug2","x is $x |$words1| $desiredWidth")
        addCharsToLineMiddle(textLine, words1, textPaint, desiredWidth, x,lineCompressMod)
    }

    /**
     * 无缩进,两端对齐
     */
    private fun addCharsToLineMiddle(
        textLine: TextLine,
        words: Array<String>,
        textPaint: TextPaint,
        desiredWidth: Float,
        startX: Float,
        lineCompressMod:Int,
    ) {
        if (!ReadBookConfig.textFullJustify) {
            addCharsToLineLast(textLine, words, textPaint, startX,lineCompressMod)
            return
        }

        var x = startX
        var d = 0f
        if(lineCompressMod==1){
            val gapCount: Int =  words.lastIndex - 1
            val endPuncWidth = StaticLayout.getDesiredWidth(words.get(gapCount+1), textPaint)
            //Log.d("debug1","endPuncWidth is $endPuncWidth")
            d = (visibleWidth- desiredWidth + endPuncWidth) / gapCount
            Log.d("debug1","$lineCompressMod $gapCount $d ${words[words.lastIndex]}")
        }else{
            val gapCount: Int =  words.lastIndex
            d = (visibleWidth - desiredWidth) / gapCount
            Log.d("debug1","$lineCompressMod $gapCount $d ${words[words.lastIndex]} $visibleWidth $desiredWidth")
        }
        words.forEachIndexed { index, s ->
            val cw = StaticLayout.getDesiredWidth(s, textPaint)
            var x1 = 0f
            if(lineCompressMod==1){
                if(index == (words.lastIndex - 1)){
                    x1 = x + (cw/2)
                    //Log.d("debug2","1 x is $x x1 is $x1")
                    textLine.addTextChar(charData = s, start = paddingLeft + x, end = paddingLeft + x1)
                }
                else if(index == words.lastIndex){
                    x1 = x + cw
                    //Log.d("debug2","2 x is $x x1 is $x1")
                    textLine.addTextChar(charData = s, start = paddingLeft + x, end = paddingLeft + x1)
                }
                else{
                    x1 = x + cw + d
                    textLine.addTextChar(charData = s, start = paddingLeft + x, end = paddingLeft + x1)
                }
                x = x1
            }
            else{
                x1 = if(index != words.lastIndex) (x + cw + d) else (x + cw)
                //Log.d("debug1","$s $x $x1 $index ${words.lastIndex} ${x1-x}")
                textLine.addTextChar(charData = s, start = paddingLeft + x, end = paddingLeft + x1)
                x = x1
            }
        }
        //Log.d("debug2","addCharsToLineMiddle $textLine")
        //exceed(textLine, words)
    }

    /**
     * 最后一行,自然排列
     */
    private fun addCharsToLineLast(
        textLine: TextLine,
        words: Array<String>,
        textPaint: TextPaint,
        startX: Float,
        lineCompressMod:Int,
    ) {
        var x = startX
        words.forEachIndexed {index, s ->
            val cw = StaticLayout.getDesiredWidth(s, textPaint)
            var x1 = 0f
            if(lineCompressMod == 1){
                if(index == (words.lastIndex - 1)){
                    x1 = x + (cw/2)
                    textLine.addTextChar(charData = s, start = paddingLeft + x, end = paddingLeft + x1)
                }
                else{
                    x1 = x + cw
                    textLine.addTextChar(charData = s, start = paddingLeft + x, end = paddingLeft + x1)
                }
                x = x1
            }else{
                x1 = x + cw
                textLine.addTextChar(charData = s, start = paddingLeft + x, end = paddingLeft + x1)
                x = x1
            }
        }
        //exceed(textLine, words)
    }

    /**
     * 超出边界处理
     */
    //todo
    private fun exceed(textLine: TextLine, words: Array<String>) {
        val endX = textLine.textChars.last().end
        //Log.d("debug2", "end is ${endX} $visibleRight")
        if (endX > visibleRight) {
            val cc = (endX - visibleRight) / words.size
            for (i in 0..words.lastIndex) {
                textLine.getTextCharReverseAt(i).let {
                    val py = cc * (words.size - i)
                    it.start = it.start - py
                    it.end = it.end - py
                }
            }
        }
    }

    /**
     * 更新样式
     */
    fun upStyle() {
        typeface = getTypeface(ReadBookConfig.textFont)
        getPaint(typeface).let {
            titlePaint = it.first
            contentPaint = it.second
        }
        //间距
        lineSpacingExtra = ReadBookConfig.lineSpacingExtra
        paragraphSpacing = ReadBookConfig.paragraphSpacing
        titleTopSpacing = ReadBookConfig.titleTopSpacing.dp
        titleBottomSpacing = ReadBookConfig.titleBottomSpacing.dp
        upVisibleSize()
    }

    private fun getTypeface(fontPath: String): Typeface {
        return try {
            when {
                fontPath.isContentScheme() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                    val fd = App.INSTANCE.contentResolver
                        .openFileDescriptor(Uri.parse(fontPath), "r")!!
                        .fileDescriptor
                    Typeface.Builder(fd).build()
                }
                fontPath.isContentScheme() -> {
                    Typeface.createFromFile(RealPathUtil.getPath(App.INSTANCE, Uri.parse(fontPath)))
                }
                fontPath.isNotEmpty() -> Typeface.createFromFile(fontPath)
                else -> when (AppConfig.systemTypefaces) {
                    1 -> Typeface.SERIF
                    2 -> Typeface.MONOSPACE
                    else -> Typeface.SANS_SERIF
                }
            }
        } catch (e: Exception) {
            ReadBookConfig.textFont = ""
            ReadBookConfig.save()
            Typeface.SANS_SERIF
        }
    }

    private fun getPaint(typeface: Typeface): Pair<TextPaint, TextPaint> {
        // 字体统一处理
        val bold = Typeface.create(typeface, Typeface.BOLD)
        val normal = Typeface.create(typeface, Typeface.NORMAL)
        val (titleFont, textFont) = when (ReadBookConfig.textBold) {
            1 -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                    Pair(Typeface.create(typeface, 900, false), bold)
                else
                    Pair(bold, bold)
            }
            2 -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                    Pair(normal, Typeface.create(typeface, 300, false))
                else
                    Pair(normal, normal)
            }
            else -> Pair(bold, normal)
        }

        //标题
        val tPaint = TextPaint()
        tPaint.color = ReadBookConfig.textColor
        tPaint.letterSpacing = ReadBookConfig.letterSpacing
        tPaint.typeface = titleFont
        tPaint.textSize = with(ReadBookConfig) { textSize + titleSize }.sp.toFloat()
        tPaint.isAntiAlias = true
        //正文
        val cPaint = TextPaint()
        cPaint.color = ReadBookConfig.textColor
        cPaint.letterSpacing = ReadBookConfig.letterSpacing
        cPaint.typeface = textFont
        cPaint.textSize = ReadBookConfig.textSize.sp.toFloat()
        cPaint.isAntiAlias = true
        return Pair(tPaint, cPaint)
    }

    /**
     * 更新View尺寸
     */
    fun upViewSize(width: Int, height: Int) {
        if (width > 0 && height > 0 && (width != viewWidth || height != viewHeight)) {
            viewWidth = width
            viewHeight = height
            upVisibleSize()
            postEvent(EventBus.UP_CONFIG, true)
        }
    }

    /**
     * 更新绘制尺寸
     */
    private fun upVisibleSize() {
        if (viewWidth > 0 && viewHeight > 0) {
            paddingLeft = ReadBookConfig.paddingLeft.dp
            paddingTop = ReadBookConfig.paddingTop.dp
            visibleWidth = viewWidth - paddingLeft - ReadBookConfig.paddingRight.dp
            visibleHeight = viewHeight - paddingTop - ReadBookConfig.paddingBottom.dp
            visibleRight = paddingLeft + visibleWidth
            visibleBottom = paddingTop + visibleHeight
        }
    }

    val TextPaint.textHeight: Float
        get() = fontMetrics.descent - fontMetrics.ascent + fontMetrics.leading
}
