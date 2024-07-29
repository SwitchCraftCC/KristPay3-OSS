/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.fabric.client.config

import me.shedaniel.clothconfig2.gui.entries.TooltipListEntry
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.Element
import net.minecraft.client.gui.Selectable
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.text.Text
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max


@Suppress("UnstableApiUsage", "DEPRECATION")
class SoundEnumListEntry<T : Any>(
  fieldName: Text,
  enumClass: Class<T>,
  value: T,
  private val defaultValue: () -> T,
  private val onValueChanged: ((T) -> Unit)?,
  private val saveConsumer: (T) -> Unit,
  private val nameProvider: (T) -> Text
) : TooltipListEntry<T>(fieldName, null, false) {
  private val values = enumClass.enumConstants
  private val index = AtomicInteger(values.indexOf(value))
  private val original = value

  private val resetButtonKey = Text.translatable("text.cloth-config.reset_value")

  private val tr = MinecraftClient.getInstance().textRenderer

  private val buttonWidget = ButtonWidget
    .builder(Text.empty()) {
      index.incrementAndGet()
      index.compareAndSet(values.size, 0)
      onValueChanged?.let { it(values[index.get()]) }
    }
    .size(150, 20)
    .build()

  private val resetButton = ButtonWidget
    .builder(resetButtonKey) {
      val idx = getDefaultIndex()
      index.set(idx)
      onValueChanged?.let { it(values[idx]) }
    }
    .size(tr.getWidth(resetButtonKey) + 6, 20)
    .build()

  private val widgets = mutableListOf<ClickableWidget>(buttonWidget, resetButton)

  override fun getValue(): T = values[index.get()]
  override fun getDefaultValue(): Optional<T> = Optional.of(defaultValue())
  private fun getDefaultIndex(): Int = max(0, values.indexOf(defaultValue()))
  override fun isEdited() = super.isEdited() || this.original != value

  override fun save() { saveConsumer(value) }

  override fun render(ctx: DrawContext, index: Int, y: Int, x: Int, entryWidth: Int, entryHeight: Int, mouseX: Int,
                      mouseY: Int, isHovered: Boolean, delta: Float) {
    super.render(ctx, index, y, x, entryWidth, entryHeight, mouseX, mouseY, isHovered, delta)

    val window = MinecraftClient.getInstance().window
    resetButton.active = isEditable && getDefaultValue().isPresent && getDefaultIndex() != this.index.get()
    resetButton.y = y
    buttonWidget.active = isEditable
    buttonWidget.y = y
    buttonWidget.message = nameProvider(value)
    val displayedFieldName = this.displayedFieldName
    if (tr.isRightToLeft) {
      ctx.drawTextWithShadow(
        tr,
        displayedFieldName.asOrderedText(),
        (window.scaledWidth - x - tr.getWidth(displayedFieldName)),
        y + 6,
        this.preferredTextColor
      )
      resetButton.x = x
      buttonWidget.x = x + resetButton.width + 2
    } else {
      ctx.drawTextWithShadow(
        tr,
        displayedFieldName.asOrderedText(),
        x,
        y + 6,
        this.preferredTextColor
      )
      resetButton.x = x + entryWidth - resetButton.width
      buttonWidget.x = x + entryWidth - 150
    }

    buttonWidget.width = 150 - resetButton.width - 2
    resetButton.render(ctx, mouseX, mouseY, delta)
    buttonWidget.render(ctx, mouseX, mouseY, delta)
  }

  override fun narratables(): MutableList<out Selectable> = widgets
  override fun children(): MutableList<out Element> = widgets
}
