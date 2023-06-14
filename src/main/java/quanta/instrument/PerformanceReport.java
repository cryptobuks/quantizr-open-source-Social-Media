package quanta.instrument;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quanta.model.client.PrincipalName;
import quanta.util.DateUtil;
import quanta.util.ThreadLocals;

public class PerformanceReport {

    private static Logger log = LoggerFactory.getLogger(PerformanceReport.class);
    // Any calls that complete faster than this time, are not even considered. They're not a problem.
    public static final int REPORT_THRESHOLD = 1300; // 1300 for prod

    public static String getReport() {
        ThreadLocals.requireAdmin();
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head>" + htmlStyle() + "</head><body>");
        sb.append(htmlH(2, "Performance Report"));
        // Sort list by whichever are consuming the most time (i.e. by duration, descending order)
        List<PerfMonEvent> orderedData;
        synchronized (Instrument.data) {
            if (Instrument.data.size() == 0) {
                sb.append("No data available yet.");
                return sb.toString();
            }
            orderedData = new ArrayList<>(Instrument.data);
            orderedData.sort((s1, s2) -> (int) (s2.duration - s1.duration));
        }
        sb.append(htmlH(3, "Events over Threshold of " + String.valueOf(REPORT_THRESHOLD)));
        int counter = 0;
        String rows = "";

        for (PerfMonEvent se : orderedData) {
            if (se.duration > REPORT_THRESHOLD) {
                rows += formatEvent(se, counter++ < 20, false);
            }
        }
        if (!rows.isEmpty()) {
            sb.append(
                htmlTable(
                    htmlTr(htmlTh("user") + htmlTh("Event") + htmlTh("Time") + htmlTh("Root Id") + htmlTh("Event Id")) + rows
                )
            );
        }
        // calculate totals per person
        HashMap<String, UserPerf> userPerfInfo = new HashMap<>();

        for (PerfMonEvent se : orderedData) {
            String user = se.user != null ? se.user : PrincipalName.ANON.s();
            UserPerf up = userPerfInfo.get(user);
            if (up == null) {
                userPerfInfo.put(user, up = new UserPerf());
                up.user = user;
            }
            up.totalCalls++;
            up.totalTime += se.duration;
        }
        List<UserPerf> upiList = new ArrayList<>(userPerfInfo.values());
        upiList.sort((s1, s2) -> (int) (s2.totalCalls - s1.totalCalls));
        // -------------------------------------------
        sb.append(htmlH(3, "Call Counts"));
        rows = "";

        for (UserPerf se : upiList) {
            rows += htmlTr(htmlTd(se.user) + htmlTdRt(String.valueOf(se.totalCalls)));
        }
        if (!rows.isEmpty()) {
            sb.append(htmlTable(htmlTr(htmlTh("user") + htmlTh("Count")) + rows));
        }
        // -------------------------------------------
        upiList.sort((s1, s2) -> (int) (s2.totalTime - s1.totalTime));
        sb.append(htmlH(3, "Time Usage by User"));
        rows = "";

        for (UserPerf se : upiList) {
            rows += htmlTr(htmlTd(se.user) + htmlTdRt(DateUtil.formatDurationMillis(se.totalTime, true)));
        }
        if (!rows.isEmpty()) {
            sb.append(htmlTable(htmlTr(htmlTh("user") + htmlTh("Total Time")) + rows));
        }
        // -------------------------------------------
        upiList.sort((s1, s2) -> (int) (s2.totalTime / s2.totalCalls - s1.totalTime / s1.totalCalls));
        sb.append(htmlH(3, "Avg Time Per Call"));
        rows = "";

        for (UserPerf se : upiList) {
            rows += htmlTr(htmlTd(se.user) + htmlTdRt(DateUtil.formatDurationMillis(se.totalTime / se.totalCalls, true)));
        }
        if (!rows.isEmpty()) {
            sb.append(htmlTable(htmlTr(htmlTh("user") + htmlTh("Avg Time")) + rows));
        }
        sb.append(getTimesPerCategory());
        sb.append("</body></html>");
        return sb.toString();
    }

    static class MethodStat {

        String category;
        int totalTime;
        int totalCount;
    }

    /* This is the most 'powerful/useful' feature, because it displays time usage for each category */
    public static String getTimesPerCategory() {
        HashMap<String, MethodStat> stats = new HashMap<>();

        for (PerfMonEvent event : Instrument.data) {
            MethodStat stat = stats.get(event.event);
            if (stat == null) {
                stats.put(event.event, stat = new MethodStat());
                stat.category = event.event;
            }
            stat.totalTime += event.duration;
            stat.totalCount++;
        }
        List<MethodStat> orderedStats = new ArrayList<>(stats.values());
        orderedStats.sort((s1, s2) -> (int) (s2.totalTime / s2.totalCount - s1.totalTime / s1.totalCount));
        String table = htmlTr(
            htmlTh("Category") +
            htmlTh("Count") +
            htmlTh("Avg. Time") + //
            htmlTh("Time")
        );

        for (MethodStat stat : orderedStats) {
            table +=
                htmlTr(
                    htmlTd(stat.category) +
                    htmlTdRt(String.valueOf(stat.totalCount)) +
                    htmlTdRt(DateUtil.formatDurationMillis(stat.totalTime / stat.totalCount, true)) + //
                    htmlTdRt(DateUtil.formatDurationMillis(stat.totalTime, true))
                );
        }
        return htmlH(3, "Times Per Category") + htmlTable(table);
    }

    /* returns as an HTML Row (user, event, rootEvent, eventId */
    public static String formatEvent(PerfMonEvent se, boolean showSubEvents, boolean isSubItem) {
        String tr = "";
        // too verbose, keeping this capability turned off for now.)
        boolean embedSubEvents = false;
        if (!isSubItem) {
            tr += htmlTd(se.user != null ? se.user : PrincipalName.ANON.s());
        }
        // If this event happens to be the head/root of a series of events
        String set = "";
        if (embedSubEvents) {
            String rows = "";
            if (showSubEvents && se.root != null && se.root.subEvents != null) {
                // sb.append("\n Set:\n");

                for (PerfMonEvent subEvent : se.root.subEvents) {
                    // if we run across same 'se' we're processing, skip it
                    if (subEvent != se) {
                        rows += formatEvent(subEvent, false, true);
                    }
                }
                if (!rows.isEmpty()) {
                    set +=
                        "<br>" +
                        htmlTable(
                            htmlTr(htmlTh("user") + htmlTh("Event") + htmlTh("Time") + htmlTh("Root Id") + htmlTh("Event Id")) +
                            rows
                        ) +
                        "<br>";
                }
            }
        }
        tr += htmlTd(se.event + set);
        tr += htmlTdRt(DateUtil.formatDurationMillis(se.duration, true));
        if (!isSubItem && se.root != null) {
            tr += htmlTdRt(String.valueOf(se.root.hashCode()));
        }
        tr += htmlTdRt(String.valueOf(se.hashCode()));
        return htmlTr(tr);
    }

    public static String htmlH(int level, String heading) {
        return "<h" + String.valueOf(level) + ">\n" + heading + "</h" + String.valueOf(level) + ">\n";
    }

    public static String htmlTable(String table) {
        return "<table>\n" + table + "</table>\n";
    }

    public static String htmlTr(String row) {
        return "<tr>\n" + row + "</tr>\n";
    }

    public static String htmlTd(String td) {
        return "<td>\n" + td + "</td>\n";
    }

    public static String htmlTdRt(String td) {
        return "<td align=\'right\'>\n" + td + "</td>\n";
    }

    public static String htmlTh(String td) {
        return "<th>\n" + td + "</th>\n";
    }

    public static String htmlStyle() {
        return (
            "<style>\n" +
            "table, th, td {\n" +
            "padding: 5px;\n" +
            "border: 1px solid black;\n" +
            "border-collapse: collapse;\n" +
            "}\n" +
            "body {padding: 20px;}" +
            "html, body {font-family: \'Courier New\', \'Courier\', \'Roboto\', \'Verdana\', \'Helvetica\', \'Arial\', \'sans-serif\' !important}" + //
            "</style>"
        );
    }
}
