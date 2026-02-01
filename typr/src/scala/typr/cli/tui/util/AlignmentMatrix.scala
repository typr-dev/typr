package typr.cli.tui.util

import tui.*
import tui.widgets.*
import typr.cli.tui.CompositeWizardState
import typr.cli.tui.FieldEditState
import typr.cli.tui.ProjectionEditState
import typr.cli.tui.FieldAlignmentState
import typr.cli.tui.AlignmentDisplayRow
import typr.cli.tui.AlignmentCellStatus
import typr.bridge.AlignmentStatus

object AlignmentMatrix {

  def render(
      f: Frame,
      area: Rect,
      wizardState: CompositeWizardState,
      selectedRow: Int,
      selectedCol: Int
  ): Unit = {
    val fields = wizardState.fields
    val projections = wizardState.projections
    val alignments = wizardState.alignments

    if (fields.isEmpty || projections.isEmpty) {
      renderEmpty(f, area, fields.isEmpty, projections.isEmpty)
      return
    }

    val fieldNames = fields.map(_.name)
    val projectionKeys = projections.map(_.key)

    val colWidth = 15
    val rowHeaderWidth = 20
    val headerHeight = 2

    val headerArea = Rect(
      x = area.x,
      y = area.y,
      width = area.width,
      height = headerHeight
    )
    renderHeader(f, headerArea, projections, rowHeaderWidth, colWidth, selectedCol)

    val bodyArea = Rect(
      x = area.x,
      y = area.y + headerHeight,
      width = area.width,
      height = area.height - headerHeight
    )
    renderBody(f, bodyArea, fieldNames, projectionKeys, alignments, rowHeaderWidth, colWidth, selectedRow, selectedCol)
  }

  private def renderEmpty(f: Frame, area: Rect, noFields: Boolean, noProjections: Boolean): Unit = {
    val message =
      if (noFields && noProjections) "Define fields and add aligned sources first"
      else if (noFields) "Define fields first (Step 2)"
      else "Add aligned sources first (Step 3)"

    val para = ParagraphWidget(
      block = Some(
        BlockWidget(
          title = Some(Spans.from(Span.styled("Field Alignment Matrix", Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)))),
          borders = Borders.ALL,
          borderType = BlockWidget.BorderType.Rounded
        )
      ),
      text = Text.from(
        Spans.from(Span.styled(message, Style(fg = Some(Color.DarkGray))))
      )
    )
    f.renderWidget(para, area)
  }

  private def renderHeader(
      f: Frame,
      area: Rect,
      projections: List[ProjectionEditState],
      rowHeaderWidth: Int,
      colWidth: Int,
      selectedCol: Int
  ): Unit = {
    val buffer = f.buffer

    buffer.setStringn(
      area.x + 1,
      area.y,
      "Field",
      rowHeaderWidth - 1,
      Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)
    )

    projections.zipWithIndex.foreach { case (proj, idx) =>
      val x = area.x + rowHeaderWidth + (idx * colWidth)
      val style =
        if (idx == selectedCol)
          Style(fg = Some(Color.White), bg = Some(Color.Blue), addModifier = Modifier.BOLD)
        else
          Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD)

      val displayName = if (proj.sourceName.length > colWidth - 2) proj.sourceName.take(colWidth - 3) + ".." else proj.sourceName
      buffer.setStringn(x, area.y, displayName, colWidth, style)
    }

    val dividerY = area.y + 1
    val lineChar = '\u2500'
    for (xOff <- 0 until math.min(area.width.toInt, rowHeaderWidth + (projections.length * colWidth))) {
      buffer.setString(area.x + xOff, dividerY, lineChar.toString, Style(fg = Some(Color.DarkGray)))
    }
  }

  private def renderBody(
      f: Frame,
      area: Rect,
      fieldNames: List[String],
      projectionKeys: List[String],
      alignments: Map[String, FieldAlignmentState],
      rowHeaderWidth: Int,
      colWidth: Int,
      selectedRow: Int,
      selectedCol: Int
  ): Unit = {
    val buffer = f.buffer

    fieldNames.zipWithIndex.foreach { case (fieldName, rowIdx) =>
      val y = area.y + rowIdx
      if (y < area.y + area.height) {
        val rowStyle =
          if (rowIdx == selectedRow)
            Style(fg = Some(Color.White), bg = Some(Color.DarkGray))
          else
            Style(fg = Some(Color.Gray))

        val displayFieldName = if (fieldName.length > rowHeaderWidth - 2) fieldName.take(rowHeaderWidth - 3) + ".." else fieldName
        buffer.setStringn(area.x + 1, y, displayFieldName, rowHeaderWidth - 1, rowStyle)

        projectionKeys.zipWithIndex.foreach { case (projKey, colIdx) =>
          val x = area.x + rowHeaderWidth + (colIdx * colWidth)

          val alignmentRow = alignments.get(projKey).flatMap { fa =>
            fa.rows.find(_.canonicalField == fieldName)
          }

          val isSelected = rowIdx == selectedRow && colIdx == selectedCol
          val (symbol, color) = alignmentRow match {
            case Some(row) =>
              row.status match {
                case AlignmentCellStatus.Aligned             => ("\u2713", Color.Green)
                case AlignmentCellStatus.Missing             => ("-", Color.DarkGray)
                case AlignmentCellStatus.Extra               => ("+", Color.Yellow)
                case AlignmentCellStatus.TypeMismatch        => ("\u2717", Color.Red)
                case AlignmentCellStatus.NullabilityMismatch => ("!", Color.Yellow)
              }
            case None => ("?", Color.DarkGray)
          }

          val mappedField = alignmentRow.flatMap(_.sourceField).getOrElse("-")
          val displayText = s"$symbol $mappedField"
          val truncated = if (displayText.length > colWidth - 1) displayText.take(colWidth - 2) + ".." else displayText

          val cellStyle =
            if (isSelected) Style(fg = Some(Color.White), bg = Some(Color.Blue), addModifier = Modifier.BOLD)
            else Style(fg = Some(color))

          buffer.setStringn(x, y, truncated, colWidth, cellStyle)
        }
      }
    }
  }

  def renderLegend(f: Frame, area: Rect): Unit = {
    val legendItems = List(
      ("\u2713", "Aligned", Color.Green),
      ("+", "Extra", Color.Yellow),
      ("!", "Nullable", Color.Yellow),
      ("\u2717", "Type mismatch", Color.Red),
      ("-", "Missing", Color.DarkGray),
      ("?", "Unknown", Color.DarkGray)
    )

    val spans = legendItems.flatMap { case (sym, label, color) =>
      List(
        Span.styled(s" $sym ", Style(fg = Some(color), addModifier = Modifier.BOLD)),
        Span.styled(s"$label ", Style(fg = Some(Color.Gray)))
      )
    }

    val para = ParagraphWidget(
      text = Text.from(Spans.from(spans*))
    )
    f.renderWidget(para, area)
  }

  def renderAlignmentDetails(f: Frame, area: Rect, wizardState: CompositeWizardState, selectedRow: Int, selectedCol: Int): Unit = {
    val fieldNames = wizardState.fields.map(_.name)
    val projections = wizardState.projections

    if (fieldNames.isEmpty || projections.isEmpty || selectedRow >= fieldNames.length || selectedCol >= projections.length) {
      return
    }

    val fieldName = fieldNames(selectedRow)
    val projection = projections(selectedCol)
    val projKey = projection.key

    val alignmentRow = wizardState.alignments.get(projKey).flatMap { fa =>
      fa.rows.find(_.canonicalField == fieldName)
    }

    val lines: List[Spans] = alignmentRow match {
      case Some(row) =>
        val statusLine = row.status match {
          case AlignmentCellStatus.Aligned =>
            Spans.from(Span.styled("Status: ", Style(fg = Some(Color.Gray))), Span.styled("Aligned", Style(fg = Some(Color.Green))))
          case AlignmentCellStatus.Missing =>
            Spans.from(Span.styled("Status: ", Style(fg = Some(Color.Gray))), Span.styled("Missing in source", Style(fg = Some(Color.DarkGray))))
          case AlignmentCellStatus.Extra =>
            Spans.from(Span.styled("Status: ", Style(fg = Some(Color.Gray))), Span.styled("Extra in source", Style(fg = Some(Color.Yellow))))
          case AlignmentCellStatus.TypeMismatch =>
            Spans.from(Span.styled("Status: ", Style(fg = Some(Color.Gray))), Span.styled("Type mismatch", Style(fg = Some(Color.Red))))
          case AlignmentCellStatus.NullabilityMismatch =>
            Spans.from(Span.styled("Status: ", Style(fg = Some(Color.Gray))), Span.styled("Nullability mismatch", Style(fg = Some(Color.Yellow))))
        }

        val mappingLine = row.sourceField match {
          case Some(sf) =>
            Spans.from(
              Span.styled("Mapped to: ", Style(fg = Some(Color.Gray))),
              Span.styled(sf, Style(fg = Some(Color.Cyan)))
            )
          case None =>
            Spans.from(Span.styled("Not mapped", Style(fg = Some(Color.DarkGray))))
        }

        val typeLines = List(
          Spans.from(
            Span.styled("Canonical type: ", Style(fg = Some(Color.Gray))),
            Span.styled(row.canonicalType, Style(fg = Some(Color.White)))
          )
        ) ++ row.sourceType.map { st =>
          Spans.from(
            Span.styled("Source type: ", Style(fg = Some(Color.Gray))),
            Span.styled(st, Style(fg = Some(Color.White)))
          )
        }

        val issueLines: List[Spans] = row.status match {
          case AlignmentCellStatus.TypeMismatch =>
            List(Spans.from(Span.styled("  - Types are not compatible", Style(fg = Some(Color.Red)))))
          case AlignmentCellStatus.NullabilityMismatch =>
            List(Spans.from(Span.styled("  - Source allows null, canonical does not", Style(fg = Some(Color.Yellow)))))
          case _ => Nil
        }

        List(
          Spans.from(Span.styled("Field: ", Style(fg = Some(Color.Gray))), Span.styled(fieldName, Style(fg = Some(Color.White)))),
          Spans.from(Span.styled("Source: ", Style(fg = Some(Color.Gray))), Span.styled(projection.sourceName, Style(fg = Some(Color.Yellow)))),
          Spans.nostyle("")
        ) ++ typeLines ++ List(Spans.nostyle(""), statusLine, mappingLine) ++ (if (issueLines.nonEmpty) Spans.nostyle("") :: issueLines else Nil)

      case None =>
        List(
          Spans.from(Span.styled("Field: ", Style(fg = Some(Color.Gray))), Span.styled(fieldName, Style(fg = Some(Color.White)))),
          Spans.from(Span.styled("Source: ", Style(fg = Some(Color.Gray))), Span.styled(projection.sourceName, Style(fg = Some(Color.Yellow)))),
          Spans.nostyle(""),
          Spans.from(Span.styled("No alignment data available", Style(fg = Some(Color.DarkGray)))),
          Spans.nostyle(""),
          Spans.from(Span.styled("Run alignment validation to see status", Style(fg = Some(Color.Cyan))))
        )
    }

    val para = ParagraphWidget(
      block = Some(
        BlockWidget(
          title = Some(Spans.from(Span.styled("Alignment Details", Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)))),
          borders = Borders.ALL,
          borderType = BlockWidget.BorderType.Rounded
        )
      ),
      text = Text.fromSpans(lines*)
    )
    f.renderWidget(para, area)
  }
}
