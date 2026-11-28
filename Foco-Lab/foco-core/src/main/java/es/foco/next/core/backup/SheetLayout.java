package es.foco.next.core.backup;

import java.util.ArrayList;
import java.util.List;

/**
 * v47: la hoja «Foco · Historial» (Sheets API v4). Aquí está todo su diseño: pestañas, columnas,
 * colores, fórmulas del Panel y gráficos. La app la crea una vez y cada noche solo añade filas; el
 * diseño no se vuelve a tocar.
 *
 * <p>La hoja usa el idioma en_US para que las fórmulas se lean siempre igual (coma como separador y
 * funciones en inglés); las fechas se muestran como AAAA-MM-DD.
 */
public final class SheetLayout {
    private SheetLayout() {}

    public static final String TITLE = "Foco · Historial";

    public static final String PANEL = "Panel",
            DIARIO = "Diario",
            POR_HORA = "Por hora",
            APPS = "Apps",
            RASTREADORES = "Rastreadores",
            EVENTOS = "Eventos",
            ANIMO = "Ánimo";
    public static final int ID_PANEL = 1,
            ID_DIARIO = 2,
            ID_POR_HORA = 3,
            ID_APPS = 4,
            ID_RASTREADORES = 5,
            ID_EVENTOS = 6,
            ID_ANIMO = 7;

    /** Pestañas con filas por día (en el orden en que se escriben). */
    public static final String[] DATA_TABS = {DIARIO, POR_HORA, APPS, RASTREADORES, EVENTOS};

    /** Colores de Foco (verdes; nada de rojo). */
    public static final String HEADER = "#2F5D4A",
            BAND = "#F7FAF6",
            CARD = "#EAF3EC",
            GREEN_LIGHT = "#9CCB86",
            GREEN_MID = "#5E9C6A",
            WHITE = "#FFFFFF";

    static final String FONT = "Roboto";

    public static final String[] DIARIO_HEAD = {
        "Fecha",
        "Modo principal",
        "Invertido (min)",
        "Retos",
        "Ocio (min)",
        "Top app",
        "Bloqueos",
        "% bloqueado NextDNS",
        "Racha (días)"
    };
    public static final String[] APPS_HEAD = {"Fecha", "App", "Minutos"};
    public static final String[] RASTREADORES_HEAD = {"Fecha", "Dominio", "Bloqueos"};
    public static final String[] EVENTOS_HEAD = {
        "Fecha", "Hora", "Tipo", "App", "Valor", "Detalle"
    };
    public static final String[] ANIMO_HEAD = {"Fecha", "Media (1-5)", "Respuestas"};

    static String[] porHoraHead() {
        String[] h = new String[25];
        h[0] = "Fecha";
        for (int i = 0; i < 24; i++) h[i + 1] = (i < 10 ? "0" : "") + i;
        return h;
    }

    public static String[] head(String tab) {
        switch (tab) {
            case DIARIO:
                return DIARIO_HEAD;
            case POR_HORA:
                return porHoraHead();
            case APPS:
                return APPS_HEAD;
            case RASTREADORES:
                return RASTREADORES_HEAD;
            case EVENTOS:
                return EVENTOS_HEAD;
            case ANIMO:
                return ANIMO_HEAD;
            default:
                return new String[0];
        }
    }

    static int idOf(String tab) {
        switch (tab) {
            case PANEL:
                return ID_PANEL;
            case DIARIO:
                return ID_DIARIO;
            case POR_HORA:
                return ID_POR_HORA;
            case APPS:
                return ID_APPS;
            case RASTREADORES:
                return ID_RASTREADORES;
            case EVENTOS:
                return ID_EVENTOS;
            default:
                return ID_ANIMO;
        }
    }

    /** 'Por hora'!A:A (con comillas si el nombre lleva espacios o tildes). */
    public static String a1(String tab, String range) {
        return "'" + tab.replace("'", "''") + "'!" + range;
    }

    // ---------------- Crear ----------------

    static Json.Obj rgb(String hex) {
        int v = Integer.parseInt(hex.substring(1), 16);
        return Json.obj()
                .put("red", ((v >> 16) & 255) / 255.0)
                .put("green", ((v >> 8) & 255) / 255.0)
                .put("blue", (v & 255) / 255.0);
    }

    static Json.Obj color(String hex) {
        return Json.obj().put("rgbColor", rgb(hex));
    }

    private static Json.Obj sheet(String title, int id, int index, int cols, int frozen) {
        return Json.obj()
                .put(
                        "properties",
                        Json.obj()
                                .put("sheetId", id)
                                .put("title", title)
                                .put("index", index)
                                .put("tabColorStyle", color(id == ID_PANEL ? HEADER : GREEN_MID))
                                .put(
                                        "gridProperties",
                                        Json.obj()
                                                .put("rowCount", id == ID_PANEL ? 80 : 1000)
                                                .put("columnCount", cols)
                                                .put("frozenRowCount", frozen)));
    }

    /** Cuerpo de spreadsheets.create. */
    public static String createBody(String timeZone, boolean mood) {
        Json.Arr sheets = new Json.Arr();
        sheets.add(sheet(PANEL, ID_PANEL, 0, 26, 0));
        sheets.add(sheet(DIARIO, ID_DIARIO, 1, DIARIO_HEAD.length, 1));
        sheets.add(sheet(POR_HORA, ID_POR_HORA, 2, 25, 1));
        sheets.add(sheet(APPS, ID_APPS, 3, APPS_HEAD.length, 1));
        sheets.add(sheet(RASTREADORES, ID_RASTREADORES, 4, RASTREADORES_HEAD.length, 1));
        sheets.add(sheet(EVENTOS, ID_EVENTOS, 5, EVENTOS_HEAD.length, 1));
        if (mood) sheets.add(sheet(ANIMO, ID_ANIMO, 6, ANIMO_HEAD.length, 1));
        return Json.obj()
                .put(
                        "properties",
                        Json.obj()
                                .put("title", TITLE)
                                .put("locale", "en_US")
                                .put("timeZone", timeZone == null ? "Etc/UTC" : timeZone)
                                .put(
                                        "defaultFormat",
                                        Json.obj()
                                                .put(
                                                        "textFormat",
                                                        Json.obj()
                                                                .put("fontFamily", FONT)
                                                                .put("fontSize", 11))))
                .put("sheets", sheets)
                .toString();
    }

    static Json.Obj range(int sheet, int r0, int r1, int c0, int c1) {
        Json.Obj g = Json.obj().put("sheetId", sheet);
        if (r0 >= 0) g.put("startRowIndex", r0);
        if (r1 >= 0) g.put("endRowIndex", r1);
        if (c0 >= 0) g.put("startColumnIndex", c0);
        if (c1 >= 0) g.put("endColumnIndex", c1);
        return g;
    }

    private static Json.Obj width(int sheet, int c0, int c1, int px) {
        return Json.obj()
                .put(
                        "updateDimensionProperties",
                        Json.obj()
                                .put(
                                        "range",
                                        Json.obj()
                                                .put("sheetId", sheet)
                                                .put("dimension", "COLUMNS")
                                                .put("startIndex", c0)
                                                .put("endIndex", c1))
                                .put("properties", Json.obj().put("pixelSize", px))
                                .put("fields", "pixelSize"));
    }

    private static Json.Obj format(Json.Obj grid, Json.Obj cellFormat, String fields) {
        return Json.obj()
                .put(
                        "repeatCell",
                        Json.obj()
                                .put("range", grid)
                                .put("cell", Json.obj().put("userEnteredFormat", cellFormat))
                                .put("fields", fields));
    }

    private static Json.Obj numberFormat(int sheet, int c0, int c1, String type, String pattern) {
        return format(
                range(sheet, 1, -1, c0, c1),
                Json.obj()
                        .put("numberFormat", Json.obj().put("type", type).put("pattern", pattern)),
                "userEnteredFormat.numberFormat");
    }

    private static Json.Obj headerText(int sheet, int cols) {
        return format(
                range(sheet, 0, 1, 0, cols),
                Json.obj()
                        .put(
                                "textFormat",
                                Json.obj()
                                        .put("bold", true)
                                        .put("fontFamily", FONT)
                                        .put("foregroundColorStyle", color(WHITE)))
                        .put("verticalAlignment", "MIDDLE"),
                "userEnteredFormat(textFormat,verticalAlignment)");
    }

    private static Json.Obj banding(int sheet, int cols) {
        return Json.obj()
                .put(
                        "addBanding",
                        Json.obj()
                                .put(
                                        "bandedRange",
                                        Json.obj()
                                                .put("range", range(sheet, 0, -1, 0, cols))
                                                .put(
                                                        "rowProperties",
                                                        Json.obj()
                                                                .put(
                                                                        "headerColorStyle",
                                                                        color(HEADER))
                                                                .put(
                                                                        "firstBandColorStyle",
                                                                        color(WHITE))
                                                                .put(
                                                                        "secondBandColorStyle",
                                                                        color(BAND)))));
    }

    /**
     * Formato de una pestaña de datos: encabezado verde con texto blanco, filas alternas, anchos.
     */
    static List<Json.Obj> dataTab(String tab) {
        int id = idOf(tab);
        String[] head = head(tab);
        List<Json.Obj> r = new ArrayList<>();
        r.add(banding(id, head.length));
        r.add(headerText(id, head.length));
        r.add(numberFormat(id, 0, 1, "DATE", "yyyy-mm-dd"));
        r.add(width(id, 0, 1, 110));
        switch (tab) {
            case DIARIO:
                r.add(width(id, 1, 2, 130));
                r.add(width(id, 2, 5, 115));
                r.add(width(id, 5, 6, 170));
                r.add(width(id, 6, 9, 150));
                r.add(numberFormat(id, 2, 5, "NUMBER", "0"));
                r.add(numberFormat(id, 6, 7, "NUMBER", "0"));
                r.add(numberFormat(id, 7, 8, "NUMBER", "0.0\"%\""));
                r.add(numberFormat(id, 8, 9, "NUMBER", "0"));
                break;
            case POR_HORA:
                r.add(width(id, 1, 25, 44));
                r.add(numberFormat(id, 1, 25, "NUMBER", "0"));
                r.add(heatmap(id));
                break;
            case APPS:
                r.add(width(id, 1, 2, 220));
                r.add(width(id, 2, 3, 90));
                break;
            case RASTREADORES:
                r.add(width(id, 1, 2, 260));
                r.add(width(id, 2, 3, 90));
                break;
            case EVENTOS:
                r.add(width(id, 1, 2, 60));
                r.add(width(id, 2, 3, 210));
                r.add(width(id, 3, 4, 170));
                r.add(width(id, 4, 5, 80));
                r.add(width(id, 5, 6, 360));
                break;
            case ANIMO:
                r.add(width(id, 1, 3, 120));
                r.add(numberFormat(id, 1, 2, "NUMBER", "0.0"));
                break;
            default:
                break;
        }
        return r;
    }

    /** Por hora: escala de verdes (poco ocio claro, mucho ocio verde oscuro). Sin rojo. */
    static Json.Obj heatmap(int id) {
        return Json.obj()
                .put(
                        "addConditionalFormatRule",
                        Json.obj()
                                .put("index", 0)
                                .put(
                                        "rule",
                                        Json.obj()
                                                .put("ranges", Json.arr(range(id, 1, -1, 1, 25)))
                                                .put(
                                                        "gradientRule",
                                                        Json.obj()
                                                                .put(
                                                                        "minpoint",
                                                                        Json.obj()
                                                                                .put(
                                                                                        "colorStyle",
                                                                                        color(BAND))
                                                                                .put(
                                                                                        "type",
                                                                                        "NUMBER")
                                                                                .put("value", "0"))
                                                                .put(
                                                                        "midpoint",
                                                                        Json.obj()
                                                                                .put(
                                                                                        "colorStyle",
                                                                                        color(
                                                                                                GREEN_LIGHT))
                                                                                .put(
                                                                                        "type",
                                                                                        "PERCENTILE")
                                                                                .put("value", "50"))
                                                                .put(
                                                                        "maxpoint",
                                                                        Json.obj()
                                                                                .put(
                                                                                        "colorStyle",
                                                                                        color(
                                                                                                HEADER))
                                                                                .put(
                                                                                        "type",
                                                                                        "MAX")))));
    }

    // ---------------- Panel ----------------

    /** Filas (0 = fila 1) de los bloques de datos que leen los gráficos. */
    static final int HELP_HEAD = 48, HELP_FIRST = 49, HOURS_ROW = 44;

    // v47b: columnas enteras (Diario!$A:$A): añadir o borrar filas nunca mueve las referencias. El
    // encabezado es texto y MAX, SUMIFS y VLOOKUP lo ignoran.
    static final String LAST = "MAX(Diario!$A:$A)";

    /** Valores y fórmulas del Panel (USER_ENTERED), como lista de {rango, filas}. */
    public static List<Object[]> panelValues() {
        List<Object[]> out = new ArrayList<>();
        out.add(new Object[] {"A1", rows(row("Foco · Panel"))});
        out.add(
                new Object[] {
                    "A2", rows(row("Lo bueno primero: lo invertido va antes que el ocio."))
                });
        out.add(
                new Object[] {
                    "A3",
                    rows(
                            row(
                                    "Invertido hoy (min)",
                                    "",
                                    "Ocio hoy (min)",
                                    "",
                                    "Racha (días)",
                                    "",
                                    "Peor horario (7 días)",
                                    "",
                                    "Rastreo bloqueado hoy"),
                            row(
                                    "=IFERROR(VLOOKUP(" + LAST + ",Diario!$A:$I,3,FALSE),0)",
                                    "",
                                    "=IFERROR(VLOOKUP(" + LAST + ",Diario!$A:$I,5,FALSE),0)",
                                    "",
                                    "=IFERROR(VLOOKUP(" + LAST + ",Diario!$A:$I,9,FALSE),0)",
                                    "",
                                    "=IF(MAX($B$45:$Y$45)=0,\"—\",TEXT(MATCH(MAX($B$45:$Y$45),$B$45:$Y$45,0)-1,\"00\")&\":00\")",
                                    "",
                                    "=IFERROR(VLOOKUP(" + LAST + ",Diario!$A:$I,8,FALSE)/100,0)"))
                });
        String thisWeek = "Diario!$A:$A,\">\"&(" + LAST + "-7)";
        String lastWeek =
                "Diario!$A:$A,\">\"&(" + LAST + "-14),Diario!$A:$A,\"<=\"&(" + LAST + "-7)";
        out.add(
                new Object[] {
                    "A6",
                    rows(
                            row("Semana actual vs anterior", "Esta", "Anterior", "Cambio"),
                            weekRow("Invertido (min)", "C", thisWeek, lastWeek),
                            weekRow("Ocio (min)", "E", thisWeek, lastWeek),
                            weekRow("Retos", "D", thisWeek, lastWeek),
                            weekRow("Bloqueos", "G", thisWeek, lastWeek))
                });
        // Ocio por hora de los últimos 7 días (para el peor horario).
        List<Object> hoursHead = new ArrayList<>();
        List<Object> hoursSum = new ArrayList<>();
        hoursHead.add("Hora");
        hoursSum.add("Ocio 7 días (min)");
        for (int h = 0; h < 24; h++) {
            String col = colName(h + 1);
            hoursHead.add((h < 10 ? "0" : "") + h);
            hoursSum.add(
                    "=SUMIFS('Por hora'!"
                            + col
                            + ":"
                            + col
                            + ",'Por hora'!$A:$A,\">=\"&("
                            + LAST
                            + "-6))");
        }
        out.add(new Object[] {"A44", rows(hoursHead, hoursSum)});
        out.add(new Object[] {"A47", rows(row("Datos de los gráficos (se calculan solos)"))});
        // Bloques que leen los gráficos (fila 49 = encabezados, 50 en adelante = datos).
        out.add(
                new Object[] {
                    "A49",
                    rows(
                            row("Fecha", "Invertido", "Ocio"),
                            row(
                                    "=IFERROR(SORT(FILTER({Diario!$A:$A,Diario!$C:$C,Diario!$E:$E},ISNUMBER(Diario!$A:$A),Diario!$A:$A>"
                                            + LAST
                                            + "-14),1,TRUE),\"\")"))
                });
        List<List<Object>> weeks = new ArrayList<>();
        weeks.add(row("Semana desde", "Ocio (min)"));
        for (int k = 1; k <= 8; k++) {
            int r = HELP_FIRST + k; // fila de la hoja (1 = primera)
            weeks.add(
                    row(
                            "=" + LAST + "-" + (7 * (8 - k) + 6),
                            "=SUMIFS(Diario!$E:$E,Diario!$A:$A,\">=\"&E"
                                    + r
                                    + ",Diario!$A:$A,\"<=\"&(E"
                                    + r
                                    + "+6))"));
        }
        out.add(new Object[] {"E49", weeks});
        out.add(new Object[] {"H49", rows(row("App", "Minutos (7 días)"), row(top("Apps")))});
        out.add(
                new Object[] {
                    "K49", rows(row("Rastreador", "Bloqueos (7 días)"), row(top("Rastreadores")))
                });
        return out;
    }

    /** Top 5 de los últimos 7 días. FILTER quita el encabezado (solo filas con fecha). */
    static String top(String tab) {
        return "=IFERROR(QUERY(FILTER("
                + tab
                + "!$A:$C,ISNUMBER("
                + tab
                + "!$A:$A)),\"select Col2, sum(Col3) where Col1 >= date '\"&TEXT("
                + LAST
                + "-6,\"yyyy-mm-dd\")&\"' group by Col2 order by sum(Col3) desc limit 5 label sum(Col3) ''\",0),\"\")";
    }

    private static List<Object> weekRow(
            String label, String col, String thisWeek, String lastWeek) {
        String a = "SUMIFS(Diario!$" + col + ":$" + col + "," + thisWeek + ")";
        String b = "SUMIFS(Diario!$" + col + ":$" + col + "," + lastWeek + ")";
        return row(label, "=" + a, "=" + b, "=" + a + "-" + b);
    }

    static String colName(int index) {
        StringBuilder b = new StringBuilder();
        int n = index + 1;
        while (n > 0) {
            int m = (n - 1) % 26;
            b.insert(0, (char) ('A' + m));
            n = (n - 1) / 26;
        }
        return b.toString();
    }

    static List<Object> row(Object... cells) {
        List<Object> r = new ArrayList<>();
        for (Object c : cells) r.add(c);
        return r;
    }

    @SafeVarargs
    static List<List<Object>> rows(List<Object>... rs) {
        List<List<Object>> out = new ArrayList<>();
        for (List<Object> r : rs) out.add(r);
        return out;
    }

    private static Json.Obj merge(int r0, int r1, int c0, int c1) {
        return Json.obj()
                .put(
                        "mergeCells",
                        Json.obj()
                                .put("range", range(ID_PANEL, r0, r1, c0, c1))
                                .put("mergeType", "MERGE_ALL"));
    }

    /** Formato del Panel: título, tarjetas, tabla semanal y los cuatro gráficos. */
    static List<Json.Obj> panel() {
        List<Json.Obj> r = new ArrayList<>();
        r.add(width(ID_PANEL, 0, 26, 100));
        r.add(width(ID_PANEL, 0, 1, 170));
        r.add(
                format(
                        range(ID_PANEL, 0, 1, 0, 6),
                        Json.obj()
                                .put(
                                        "textFormat",
                                        Json.obj()
                                                .put("bold", true)
                                                .put("fontSize", 20)
                                                .put("fontFamily", FONT)
                                                .put("foregroundColorStyle", color(HEADER))),
                        "userEnteredFormat.textFormat"));
        // Tarjetas: etiqueta pequeña y número grande, sobre verde muy claro.
        for (int c = 0; c < 10; c += 2) {
            r.add(merge(2, 3, c, c + 2));
            r.add(merge(3, 4, c, c + 2));
        }
        r.add(
                format(
                        range(ID_PANEL, 2, 3, 0, 10),
                        Json.obj()
                                .put("backgroundColorStyle", color(CARD))
                                .put("horizontalAlignment", "CENTER")
                                .put(
                                        "textFormat",
                                        Json.obj()
                                                .put("fontSize", 10)
                                                .put("foregroundColorStyle", color(HEADER))),
                        "userEnteredFormat(backgroundColorStyle,horizontalAlignment,textFormat)"));
        r.add(
                format(
                        range(ID_PANEL, 3, 4, 0, 10),
                        Json.obj()
                                .put("backgroundColorStyle", color(CARD))
                                .put("horizontalAlignment", "CENTER")
                                .put(
                                        "numberFormat",
                                        Json.obj().put("type", "NUMBER").put("pattern", "0"))
                                .put(
                                        "textFormat",
                                        Json.obj()
                                                .put("bold", true)
                                                .put("fontSize", 22)
                                                .put("foregroundColorStyle", color(HEADER))),
                        "userEnteredFormat(backgroundColorStyle,horizontalAlignment,numberFormat,textFormat)"));
        r.add(
                format(
                        range(ID_PANEL, 3, 4, 8, 10),
                        Json.obj()
                                .put(
                                        "numberFormat",
                                        Json.obj().put("type", "PERCENT").put("pattern", "0.0%")),
                        "userEnteredFormat.numberFormat"));
        r.add(
                Json.obj()
                        .put(
                                "updateDimensionProperties",
                                Json.obj()
                                        .put(
                                                "range",
                                                Json.obj()
                                                        .put("sheetId", ID_PANEL)
                                                        .put("dimension", "ROWS")
                                                        .put("startIndex", 3)
                                                        .put("endIndex", 4))
                                        .put("properties", Json.obj().put("pixelSize", 52))
                                        .put("fields", "pixelSize")));
        // Tabla semanal: encabezado verde con texto blanco y filas alternas.
        r.add(
                format(
                        range(ID_PANEL, 5, 6, 0, 4),
                        Json.obj()
                                .put("backgroundColorStyle", color(HEADER))
                                .put(
                                        "textFormat",
                                        Json.obj()
                                                .put("bold", true)
                                                .put("foregroundColorStyle", color(WHITE))),
                        "userEnteredFormat(backgroundColorStyle,textFormat)"));
        r.add(
                format(
                        range(ID_PANEL, 7, 8, 0, 4),
                        Json.obj().put("backgroundColorStyle", color(BAND)),
                        "userEnteredFormat.backgroundColorStyle"));
        r.add(
                format(
                        range(ID_PANEL, 9, 10, 0, 4),
                        Json.obj().put("backgroundColorStyle", color(BAND)),
                        "userEnteredFormat.backgroundColorStyle"));
        r.add(
                format(
                        range(ID_PANEL, 6, 10, 1, 4),
                        Json.obj()
                                .put(
                                        "numberFormat",
                                        Json.obj().put("type", "NUMBER").put("pattern", "0")),
                        "userEnteredFormat.numberFormat"));
        // Encabezados de los bloques de datos.
        for (int[] c : new int[][] {{0, 3}, {4, 6}, {7, 9}, {10, 12}})
            r.add(
                    format(
                            range(ID_PANEL, HELP_HEAD, HELP_HEAD + 1, c[0], c[1]),
                            Json.obj()
                                    .put("backgroundColorStyle", color(HEADER))
                                    .put(
                                            "textFormat",
                                            Json.obj()
                                                    .put("bold", true)
                                                    .put("foregroundColorStyle", color(WHITE))),
                            "userEnteredFormat(backgroundColorStyle,textFormat)"));
        r.add(
                format(
                        range(ID_PANEL, HELP_FIRST, HELP_FIRST + 14, 0, 1),
                        Json.obj()
                                .put(
                                        "numberFormat",
                                        Json.obj().put("type", "DATE").put("pattern", "mm-dd")),
                        "userEnteredFormat.numberFormat"));
        r.add(
                format(
                        range(ID_PANEL, HELP_FIRST, HELP_FIRST + 8, 4, 5),
                        Json.obj()
                                .put(
                                        "numberFormat",
                                        Json.obj().put("type", "DATE").put("pattern", "mm-dd")),
                        "userEnteredFormat.numberFormat"));
        r.add(
                format(
                        range(ID_PANEL, HOURS_ROW - 1, HOURS_ROW, 0, 25),
                        Json.obj()
                                .put(
                                        "textFormat",
                                        Json.obj()
                                                .put("bold", true)
                                                .put("foregroundColorStyle", color(HEADER))),
                        "userEnteredFormat.textFormat"));
        r.addAll(charts());
        return r;
    }

    /** Títulos de los cuatro gráficos del Panel (así se reconocen para repararlos). */
    public static final String[] CHART_TITLES = {
        "Invertido vs ocio (últimos 14 días)",
        "Ocio por semana",
        "Top apps de ocio (7 días)",
        "Top rastreadores bloqueados (7 días)"
    };

    /**
     * Versión del diseño. v47b (2): fórmulas con columnas enteras, filas nuevas sin insertar y
     * fecha con formato; las hojas creadas antes se reparan solas.
     */
    public static final int DESIGN = 2;

    /** Los cuatro gráficos (leen los bloques de abajo del Panel, que leen las otras pestañas). */
    static List<Json.Obj> charts() {
        List<Json.Obj> r = new ArrayList<>();
        r.add(
                chart(
                        "Invertido vs ocio (últimos 14 días)",
                        "COLUMN",
                        range(ID_PANEL, HELP_HEAD, HELP_FIRST + 14, 0, 1),
                        new Json.Obj[] {
                            range(ID_PANEL, HELP_HEAD, HELP_FIRST + 14, 1, 2),
                            range(ID_PANEL, HELP_HEAD, HELP_FIRST + 14, 2, 3)
                        },
                        new String[] {HEADER, GREEN_LIGHT},
                        11,
                        0));
        r.add(
                chart(
                        "Ocio por semana",
                        "LINE",
                        range(ID_PANEL, HELP_HEAD, HELP_FIRST + 8, 4, 5),
                        new Json.Obj[] {range(ID_PANEL, HELP_HEAD, HELP_FIRST + 8, 5, 6)},
                        new String[] {GREEN_MID},
                        11,
                        6));
        r.add(
                chart(
                        "Top apps de ocio (7 días)",
                        "BAR",
                        range(ID_PANEL, HELP_HEAD, HELP_FIRST + 5, 7, 8),
                        new Json.Obj[] {range(ID_PANEL, HELP_HEAD, HELP_FIRST + 5, 8, 9)},
                        new String[] {GREEN_MID},
                        27,
                        0));
        r.add(
                chart(
                        "Top rastreadores bloqueados (7 días)",
                        "BAR",
                        range(ID_PANEL, HELP_HEAD, HELP_FIRST + 5, 10, 11),
                        new Json.Obj[] {range(ID_PANEL, HELP_HEAD, HELP_FIRST + 5, 11, 12)},
                        new String[] {HEADER},
                        27,
                        6));
        return r;
    }

    /**
     * Google quita las series de un gráfico si al crearlo no había datos. Este batchUpdate borra
     * los gráficos rotos ({@code deleteIds}) y vuelve a crear los que falten o estaban rotos
     * ({@code titles}).
     */
    public static String chartsBody(List<String> titles, List<Integer> deleteIds) {
        Json.Arr req = new Json.Arr();
        for (Integer id : deleteIds)
            req.add(Json.obj().put("deleteEmbeddedObject", Json.obj().put("objectId", id)));
        List<Json.Obj> all = charts();
        for (int i = 0; i < CHART_TITLES.length; i++)
            if (titles.contains(CHART_TITLES[i])) req.add(all.get(i));
        return Json.obj().put("requests", req).toString();
    }

    /** Reparación de hojas creadas con el diseño 1: la fecha con formato AAAA-MM-DD. */
    public static String repairBody(boolean mood) {
        Json.Arr req = new Json.Arr();
        for (String tab : DATA_TABS) req.add(numberFormat(idOf(tab), 0, 1, "DATE", "yyyy-mm-dd"));
        if (mood) req.add(numberFormat(ID_ANIMO, 0, 1, "DATE", "yyyy-mm-dd"));
        return Json.obj().put("requests", req).toString();
    }

    static Json.Obj chart(
            String title,
            String type,
            Json.Obj domain,
            Json.Obj[] series,
            String[] colors,
            int anchorRow,
            int anchorCol) {
        boolean bar = "BAR".equals(type);
        Json.Arr s = new Json.Arr();
        for (int i = 0; i < series.length; i++)
            s.add(
                    Json.obj()
                            .put(
                                    "series",
                                    Json.obj()
                                            .put(
                                                    "sourceRange",
                                                    Json.obj().put("sources", Json.arr(series[i]))))
                            .put("targetAxis", bar ? "BOTTOM_AXIS" : "LEFT_AXIS")
                            .put("colorStyle", color(colors[i])));
        Json.Obj basic =
                Json.obj()
                        .put("chartType", type)
                        .put("legendPosition", series.length > 1 ? "BOTTOM_LEGEND" : "NO_LEGEND")
                        .put("headerCount", 1)
                        .put(
                                "axis",
                                Json.arr(
                                        Json.obj().put("position", "BOTTOM_AXIS"),
                                        Json.obj().put("position", "LEFT_AXIS")))
                        .put(
                                "domains",
                                Json.arr(
                                        Json.obj()
                                                .put(
                                                        "domain",
                                                        Json.obj()
                                                                .put(
                                                                        "sourceRange",
                                                                        Json.obj()
                                                                                .put(
                                                                                        "sources",
                                                                                        Json.arr(
                                                                                                domain))))))
                        .put("series", s);
        return Json.obj()
                .put(
                        "addChart",
                        Json.obj()
                                .put(
                                        "chart",
                                        Json.obj()
                                                .put(
                                                        "spec",
                                                        Json.obj()
                                                                .put("title", title)
                                                                .put("fontName", FONT)
                                                                .put("basicChart", basic))
                                                .put(
                                                        "position",
                                                        Json.obj()
                                                                .put(
                                                                        "overlayPosition",
                                                                        Json.obj()
                                                                                .put(
                                                                                        "anchorCell",
                                                                                        Json.obj()
                                                                                                .put(
                                                                                                        "sheetId",
                                                                                                        ID_PANEL)
                                                                                                .put(
                                                                                                        "rowIndex",
                                                                                                        anchorRow)
                                                                                                .put(
                                                                                                        "columnIndex",
                                                                                                        anchorCol))
                                                                                .put(
                                                                                        "widthPixels",
                                                                                        600)
                                                                                .put(
                                                                                        "heightPixels",
                                                                                        310)))));
    }

    /** batchUpdate del diseño (una sola vez, tras crear la hoja). */
    public static String designBody(boolean mood) {
        Json.Arr req = new Json.Arr();
        for (Json.Obj o : panel()) req.add(o);
        for (String tab : DATA_TABS) for (Json.Obj o : dataTab(tab)) req.add(o);
        if (mood) for (Json.Obj o : dataTab(ANIMO)) req.add(o);
        return Json.obj().put("requests", req).toString();
    }

    /** batchUpdate para añadir la pestaña Ánimo después (si se activa más tarde). */
    public static String addMoodBody() {
        Json.Arr req = new Json.Arr();
        req.add(Json.obj().put("addSheet", sheet(ANIMO, ID_ANIMO, 6, ANIMO_HEAD.length, 1)));
        for (Json.Obj o : dataTab(ANIMO)) req.add(o);
        return Json.obj().put("requests", req).toString();
    }

    /** values:batchUpdate con encabezados y Panel (USER_ENTERED, para que las fórmulas valgan). */
    public static String headersAndPanelBody(boolean mood) {
        Json.Arr data = new Json.Arr();
        for (String tab : DATA_TABS)
            data.add(valueRange(a1(tab, "A1"), rows(row((Object[]) head(tab)))));
        if (mood) data.add(valueRange(a1(ANIMO, "A1"), rows(row((Object[]) ANIMO_HEAD))));
        for (Object[] p : panelValues()) {
            @SuppressWarnings("unchecked")
            List<List<Object>> v = (List<List<Object>>) p[1];
            data.add(valueRange(a1(PANEL, (String) p[0]), v));
        }
        return Json.obj().put("valueInputOption", "USER_ENTERED").put("data", data).toString();
    }

    public static String moodHeadersBody() {
        Json.Arr data = new Json.Arr();
        data.add(valueRange(a1(ANIMO, "A1"), rows(row((Object[]) ANIMO_HEAD))));
        return Json.obj().put("valueInputOption", "USER_ENTERED").put("data", data).toString();
    }

    static Json.Obj valueRange(String range, List<List<Object>> values) {
        return Json.obj().put("range", range).put("majorDimension", "ROWS").put("values", values);
    }

    /** batchUpdate que borra tramos de filas (de {@link SheetRows}) en una pestaña. */
    public static String deleteRowsBody(int sheetId, List<int[]> ranges) {
        Json.Arr req = new Json.Arr();
        for (int[] r : ranges)
            req.add(
                    Json.obj()
                            .put(
                                    "deleteDimension",
                                    Json.obj()
                                            .put(
                                                    "range",
                                                    Json.obj()
                                                            .put("sheetId", sheetId)
                                                            .put("dimension", "ROWS")
                                                            .put("startIndex", r[0])
                                                            .put("endIndex", r[1]))));
        return Json.obj().put("requests", req).toString();
    }

    public static int sheetId(String tab) {
        return idOf(tab);
    }

    // ---------------- Filas de un día ----------------

    /** Filas que el día añade a cada pestaña (sin encabezado). */
    public static List<List<Object>> rowsFor(String tab, DayReport d) {
        String day = d.day.toString();
        List<List<Object>> out = new ArrayList<>();
        switch (tab) {
            case DIARIO:
                out.add(
                        row(
                                day,
                                modeLabel(d.mainMode()),
                                d.investedMin(),
                                d.challenges,
                                d.leisureMin(),
                                d.leisureApps.isEmpty() ? "—" : plain(d.leisureApps.get(0).name),
                                d.blocks,
                                d.nextDns == null ? "" : d.nextDns.pctBlocked(),
                                d.streak));
                break;
            case POR_HORA:
                {
                    List<Object> r = row(day);
                    for (long v : d.leisureByHour) r.add(v);
                    out.add(r);
                }
                break;
            case APPS:
                for (DayReport.Item it : d.leisureApps) out.add(row(day, plain(it.name), it.value));
                break;
            case RASTREADORES:
                if (d.nextDns != null)
                    for (DayReport.Item it : d.nextDns.trackers)
                        out.add(row(day, plain(it.name), it.value));
                break;
            case EVENTOS:
                for (DayReport.Event e : d.events)
                    out.add(
                            row(
                                    day,
                                    e.hour < 0 ? "" : (e.hour < 10 ? "0" : "") + e.hour + ":00",
                                    e.type,
                                    e.app,
                                    e.value,
                                    plain(e.detail)));
                break;
            case ANIMO:
                if (d.moodCount > 0) out.add(row(day, d.moodAverage, d.moodCount));
                break;
            default:
                break;
        }
        return out;
    }

    /** Un texto nunca se toma por fórmula en la hoja. */
    static String plain(String s) {
        if (s == null) return "";
        String t = s.length() > 300 ? s.substring(0, 300) : s;
        return !t.isEmpty() && "=+-@".indexOf(t.charAt(0)) >= 0 ? "'" + t : t;
    }

    static String modeLabel(String mode) {
        switch (mode) {
            case "CUIDADO":
                return "Cuidado";
            case "ENFOQUE":
                return "Enfoque";
            default:
                return "Normal";
        }
    }

    /** values:append para filas (USER_ENTERED: la fecha queda como fecha). */
    public static String appendBody(List<List<Object>> rows) {
        return Json.obj().put("majorDimension", "ROWS").put("values", rows).toString();
    }
}
