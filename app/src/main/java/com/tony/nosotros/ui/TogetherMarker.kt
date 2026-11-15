package com.tony.nosotros.ui

import android.content.Context
import android.graphics.*
import androidx.core.graphics.PathParser

/** Static portraits are cached by style; animation moves layers, never redraws photographs. */
internal fun togetherBitmap(ctx: Context, people: List<MapPerson>): Bitmap {
    val d=ctx.resources.displayMetrics.density
    val bitmap=Bitmap.createBitmap((144*d).toInt(),(110*d).toInt(),Bitmap.Config.ARGB_8888)
    bitmap.density=ctx.resources.displayMetrics.densityDpi
    val c=Canvas(bitmap); c.scale(d,d)
    val p=Paint(Paint.ANTI_ALIAS_FLAG)
    p.color=Color.rgb(180,135,235)
    c.drawRoundRect(RectF(14f,24f,130f,84f),30f,30f,p)
    people.take(2).forEachIndexed { index, person ->
        val x=if(index==0) 45f else 99f; val y=54f
        p.color=Color.WHITE; c.drawCircle(x,y,27f,p)
        p.color=person.color; c.drawCircle(x,y,23f,p)
        val photo=decodeAvatar(person.avatar)
        if(photo!=null) {
            c.save(); c.clipPath(Path().apply { addCircle(x,y,23f,Path.Direction.CW) })
            c.drawBitmap(photo,null,RectF(x-23,y-23,x+23,y+23),p); c.restore()
        } else {
            p.color=person.ink; p.textSize=20f; p.textAlign=Paint.Align.CENTER
            p.typeface=Typeface.create(Typeface.DEFAULT,Typeface.BOLD)
            c.drawText(person.initial,x,y-(p.ascent()+p.descent())/2,p)
        }
    }
    p.color=Color.rgb(245,223,246)
    c.drawRoundRect(RectF(39f,88f,105f,108f),10f,10f,p)
    p.color=Color.rgb(98,61,131); p.textSize=12f; p.textAlign=Paint.Align.CENTER
    p.typeface=Typeface.create(Typeface.DEFAULT,Typeface.BOLD)
    c.drawText("Juntos",72f,98f-(p.ascent()+p.descent())/2,p)
    return bitmap
}

internal fun togetherHeartBitmap(ctx: Context): Bitmap {
    val d=ctx.resources.displayMetrics.density
    val bitmap=Bitmap.createBitmap((28*d).toInt(),(28*d).toInt(),Bitmap.Config.ARGB_8888)
    bitmap.density=ctx.resources.displayMetrics.densityDpi
    val c=Canvas(bitmap); c.scale(d,d); c.translate(2f,2f)
    val p=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(240,137,197) }
    val heart=PathParser.createPathFromPathData("M12 21L3.5 12.5C-3 6 5-2 12 5C19-2 27 6 20.5 12.5Z")!!
    c.drawPath(heart,p)
    return bitmap
}
