package cwms.cda.data.dao;

import com.google.common.flogger.FluentLogger;

import cwms.cda.data.dto.timeSeriesText.RegularTextTimeSeriesRow;
import cwms.cda.data.dto.timeSeriesText.StandardTextCatalog;
import cwms.cda.data.dto.timeSeriesText.StandardTextId;
import cwms.cda.data.dto.timeSeriesText.StandardTextTimeSeriesRow;
import cwms.cda.data.dto.timeSeriesText.StandardTextValue;
import cwms.cda.data.dto.timeSeriesText.TextTimeSeries;
import hec.data.ITimeSeriesDescription;
import hec.data.timeSeriesText.DateDateKey;
import hec.data.timeSeriesText.TextTimeSeriesRow;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;

import java.util.stream.Collectors;
import kotlin.Pair;
import org.apache.commons.codec.StringDecoder;
import org.jetbrains.annotations.NotNull;
import org.jooq.Condition;
import org.jooq.Cursor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record8;
import org.jooq.Result;
import org.jooq.ResultQuery;
import org.jooq.SelectJoinStep;
import org.jooq.conf.ParamType;
import org.jooq.exception.NoDataFoundException;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultBinding;
import usace.cwms.db.dao.ifc.text.CwmsDbText;
import usace.cwms.db.dao.util.OracleTypeMap;
import usace.cwms.db.dao.util.services.CwmsDbServiceLookup;
import usace.cwms.db.jooq.codegen.packages.CWMS_TEXT_PACKAGE;

// based on https://bitbucket.hecdev.net/projects/CWMS/repos/hec-cwms-data-access/browse/hec-db-jdbc/src/main/java/wcds/dbi/oracle/cwms/CwmsTimeSeriesTextJdbcDao.java
public final class TimeSeriesTextDao extends JooqDao<TextTimeSeries> {
    private static final FluentLogger logger = FluentLogger.forEnclosingClass();
    private static final TimeZone DEFAULT_TIME_ZONE = TimeZone.getDefault();
    private final SimpleDateFormat _dateTimeFormatter = new SimpleDateFormat("dd-MMM-yyyy "
            + "HH:mm:ss");

    public static final String OFFICE_ID = "OFFICE_ID";
    private static final String TEXT = "TEXT";
    private static final String TEXT_ID = "TEXT_ID";
    private static final int TEXT_DOES_NOT_EXIST_ERROR_CODE = 20034;
    private static final int TEXT_ID_DOES_NOT_EXIST_ERROR_CODE = 20001;
    private static final String ATTRIBUTE = "ATTRIBUTE";
    private static final String STD_TEXT_ID = "STD_TEXT_ID";
    private static final String DATA_ENTRY_DATE = "DATA_ENTRY_DATE";
    private static final String VERSION_DATE = "VERSION_DATE";
    private static final String DATE_TIME = "DATE_TIME";
    private static final String STD_TEXT = "STD_TEXT";

    private static List<String> timeSeriesStdTextColumnsList;
    private static List<String> stdTextCatalogColumnsList;
    private static List<String> timeSeriesTextColumnsList;

    static {
        String[] array = new String[]{DATE_TIME, VERSION_DATE, DATA_ENTRY_DATE, TEXT_ID,
                ATTRIBUTE, TEXT};
        Arrays.sort(array);
        timeSeriesTextColumnsList = Arrays.asList(array);
    }

    static {
        String[] array = new String[]{OFFICE_ID, STD_TEXT_ID, STD_TEXT};
        Arrays.sort(array);
        stdTextCatalogColumnsList = Arrays.asList(array);
    }

    static {
        String[] array = new String[]{DATE_TIME, VERSION_DATE, DATA_ENTRY_DATE, STD_TEXT_ID,
                ATTRIBUTE, STD_TEXT};
        Arrays.sort(array);
        timeSeriesStdTextColumnsList = Arrays.asList(array);
    }


    public TimeSeriesTextDao(DSLContext dsl) {
        super(dsl);
    }


    public StandardTextCatalog getStandardTextCatalog(String pOfficeIdMask,
                                                      String pStdTextIdMask) throws SQLException {

        try (ResultSet rs = CWMS_TEXT_PACKAGE.call_CAT_STD_TEXT_F(dsl.configuration(),
                pStdTextIdMask, pOfficeIdMask).intoResultSet()) {

            return parseStandardTextResultSet(rs);
        }

    }

    private static StandardTextCatalog parseStandardTextResultSet(ResultSet rs) throws SQLException {
        OracleTypeMap.checkMetaData(rs.getMetaData(), stdTextCatalogColumnsList, "Standard Text "
                + "Catalog");
        StandardTextCatalog.Builder builder = new StandardTextCatalog.Builder();
        while (rs.next()) {
            builder.withValue(buildStandardTextValue(
                    rs.getString(OFFICE_ID), rs.getString(STD_TEXT_ID), rs.getString(STD_TEXT)));
        }
        return builder.build();
    }

    private static StandardTextValue buildStandardTextValue(String officeId, String txtId,
                                                            String txt) {
        cwms.cda.data.dto.timeSeriesText.StandardTextId id =
                new cwms.cda.data.dto.timeSeriesText.StandardTextId.Builder()
                        .withOfficeId(officeId)
                        .withId(txtId)
                        .build();
        return  new StandardTextValue.Builder()
                .withId(id)
                .withStandardText(txt)
                .build();
    }


    public Timestamp createTimestamp(Date date) {
        Timestamp retval = null;
        if (date != null) {
            long time = date.getTime();
            retval = createTimestamp(time);
        }
        return retval;
    }

    public Timestamp createTimestamp(long date) {

        if (logger.atFinest().isEnabled()) {
            TimeZone defaultTimeZone = DEFAULT_TIME_ZONE;
            String defaultTimeZoneDisplayName =
                    " " + defaultTimeZone.getDisplayName(defaultTimeZone.inDaylightTime(new Date(date)), TimeZone.SHORT);
            TimeZone gmtTimeZone = OracleTypeMap.GMT_TIME_ZONE;
            Date convertedDate = new Date(date);
            String utcTimeZoneDisplayName =
                    " " + gmtTimeZone.getDisplayName(gmtTimeZone.inDaylightTime(convertedDate),
                            TimeZone.SHORT);
            logger.atFinest().log("Storing date: " + _dateTimeFormatter.format(date) + defaultTimeZoneDisplayName +
                    " converted to UTC date: " + _dateTimeFormatter.format(convertedDate) + utcTimeZoneDisplayName);
        }
        return new Timestamp(date);
    }

    public String createTimeZoneId(TimeZone timeZone) {
        String retval = null;
        if (timeZone != null) {
            retval = timeZone.getID();
        }
        return retval;
    }


    public ResultSet retrieveTsTextF(String pTsid, String textMask,
                                     Date startTime, Date endTime, Date versionDate,
                                     TimeZone timeZone, boolean maxVersion,
                                     Long minAttribute, Long maxAttribute, String officeId) throws SQLException {
        Timestamp pStartTime = createTimestamp(startTime);
        Timestamp pEndTime = createTimestamp(endTime);
        Timestamp pVersionDate = createTimestamp(versionDate);
        String pTimeZone = createTimeZoneId(timeZone);
        String pMaxVersion = OracleTypeMap.formatBool(maxVersion);
        return CWMS_TEXT_PACKAGE.call_RETRIEVE_TS_TEXT_F(dsl.configuration(),
                pTsid, textMask,
                pStartTime,
                pEndTime,
                pVersionDate,
                pTimeZone,
                pMaxVersion, minAttribute, maxAttribute, officeId).intoResultSet();
    }

    public TextTimeSeries<RegularTextTimeSeriesRow> retrieveTimeSeriesText(
            ITimeSeriesDescription timeSeriesDescription, String textMask, Date startTime,
            Date endTime, Date versionDate,
            boolean maxVersion, Long minAttribute, Long maxAttribute) throws RuntimeException {
        try {
            String pTsid = timeSeriesDescription.toString();
            TimeZone timeZone = OracleTypeMap.GMT_TIME_ZONE;
            String officeId = timeSeriesDescription.getOfficeId();

            try (ResultSet retrieveTsTextF = retrieveTsTextF(pTsid, textMask,
                    startTime, endTime, versionDate, timeZone, maxVersion, minAttribute,
                    maxAttribute,
                    officeId)) {
                return parseTimeSeriesTextResultSet(timeSeriesDescription, retrieveTsTextF);
            }
        } catch (SQLException e) {
            if (e.getErrorCode() == TEXT_DOES_NOT_EXIST_ERROR_CODE || e.getErrorCode() == TEXT_ID_DOES_NOT_EXIST_ERROR_CODE) {
                throw new NoDataFoundException();
            } else {
                throw new RuntimeException(e);  // TODO: wrap with something else.
            }
        }
    }

    private TextTimeSeries<RegularTextTimeSeriesRow> parseTimeSeriesTextResultSet(ITimeSeriesDescription timeSeriesDescription, ResultSet rs) throws SQLException {
        OracleTypeMap.checkMetaData(rs.getMetaData(), timeSeriesTextColumnsList, "Text Time Series");

        TextTimeSeries.Builder<RegularTextTimeSeriesRow> builder = new TextTimeSeries.Builder<>();
        builder.withId(timeSeriesDescription.getTimeSeriesId())
                .withOfficeId(timeSeriesDescription.getOfficeId());

        while (rs.next()) {
            RegularTextTimeSeriesRow row = parseRegularRow(rs);
            builder.withRow(row);

        }
        return builder.build();
    }

    private static RegularTextTimeSeriesRow parseRegularRow(ResultSet rs) throws SQLException {
        RegularTextTimeSeriesRow.Builder builder = new RegularTextTimeSeriesRow.Builder();

        Timestamp tsDateTime = rs.getTimestamp(DATE_TIME,
                OracleTypeMap.getInstance().getGmtCalendar());
        if (!rs.wasNull()) {
            Date dateTime = new Date(tsDateTime.getTime());
            builder.withDateTime(dateTime);
        }

        Timestamp tsVersionDate = rs.getTimestamp(VERSION_DATE,
                OracleTypeMap.getInstance().getGmtCalendar());
        if (!rs.wasNull()) {
            Date versionDate = new Date(tsVersionDate.getTime());
            builder.withVersionDate(versionDate);
        }
        Timestamp tsDataEntryDate = rs.getTimestamp(DATA_ENTRY_DATE,
                OracleTypeMap.getInstance().getGmtCalendar());
        if (!rs.wasNull()) {
            Date dataEntryDate = new Date(tsDataEntryDate.getTime());
            builder.withDataEntryDate(dataEntryDate);
        }
        String textId = rs.getString(TEXT_ID);
        if (!rs.wasNull()) {
            builder.withTextId(textId);
        }
        Number attribute = rs.getLong(ATTRIBUTE);
        if (!rs.wasNull()) {
            builder.withAttribute(attribute.longValue());
        }
        String clobString = rs.getString(TEXT);
        if (!rs.wasNull()) {
            builder.withTextValue(clobString);
        }
        return builder.build();
    }


    protected void setThreadTimeZone(String timeZone) {
        if (timeZone == null) {
            timeZone = System.getProperty("cwms.default.timezone");
            if (timeZone == null) {
                timeZone = "UTC";
            }
        }
        TimeZone calTimeZone = TimeZone.getTimeZone(timeZone);
        DefaultBinding.THREAD_LOCAL.set(Calendar.getInstance(calTimeZone));
    }


    protected void setThreadTimeZone(TimeZone timeZone) {
        if (timeZone == null) {
            timeZone = TimeZone.getTimeZone("UTC");
        }
        String defaultTimeZone = System.getProperty("cwms.default.timezone");
        if (defaultTimeZone != null) {
            timeZone = TimeZone.getTimeZone(defaultTimeZone);
        }
        DefaultBinding.THREAD_LOCAL.set(Calendar.getInstance(timeZone));
    }


    public void deleteStandardText(StandardTextId standardTextId, DeleteRule deleteAction) {
        String stdTextId = standardTextId.getId();
        String deleteActionString = deleteAction.toString();
        String officeId = standardTextId.getOfficeId();

        connection(dsl, c -> {
            CwmsDbText dbText = CwmsDbServiceLookup.buildCwmsDb(CwmsDbText.class, c);
            dbText.deleteStdText(c, stdTextId, deleteActionString, officeId);
        });
    }


    public StandardTextValue retrieveStandardText(StandardTextId standardTextId) {

        return connectionResult(dsl, c -> {
            CwmsDbText dbText = CwmsDbServiceLookup.buildCwmsDb(CwmsDbText.class, c);

            String stdTextClob = dbText.retrieveStdTextF(c,
                    standardTextId.getId(),
                    standardTextId.getOfficeId());

            return new StandardTextValue.Builder()
                    .withId(standardTextId)
                    .withStandardText(stdTextClob)
                    .build();
        });
    }


    public void storeStandardText(StandardTextValue standardTextValue,
                                  boolean failIfExists) {

        cwms.cda.data.dto.timeSeriesText.StandardTextId standardTextId = standardTextValue.getId();
        String stdTextId = standardTextId.getId();
        String stdText = standardTextValue.getStandardText();
        String officeId = standardTextId.getOfficeId();

        connection(dsl, c -> {
            CwmsDbText dbText = CwmsDbServiceLookup.buildCwmsDb(CwmsDbText.class, c);
            dbText.storeStdText(c, stdTextId, stdText, failIfExists, officeId);
        });
    }


    public void deleteTimeSeriesStandardText(
            ITimeSeriesDescription timeSeriesDescription,
            StandardTextId standardTextId, Date startTime,
            Date endTime, Date versionDate, boolean maxVersion,
            Long minAttribute, Long maxAttribute) {

        String tsid = timeSeriesDescription.toString();
        String stdTextIdMask = standardTextId.getId();
        TimeZone timeZone = OracleTypeMap.GMT_TIME_ZONE;
        String officeId = timeSeriesDescription.getOfficeId();

        connection(dsl, c -> {
            CwmsDbText dbText = CwmsDbServiceLookup.buildCwmsDb(CwmsDbText.class, c);
            dbText.deleteTsStdText(c, tsid, stdTextIdMask, startTime,
                    endTime, versionDate, timeZone, maxVersion, minAttribute,
                    maxAttribute, officeId);
        });
    }


    public TextTimeSeries<StandardTextTimeSeriesRow> retrieveTimeSeriesStandardText(
            ITimeSeriesDescription timeSeriesDescription, StandardTextId standardTextId,
            Date startTime, Date endTime,
            Date versionDate, boolean maxVersion, boolean retrieveText, Long minAttribute,
            Long maxAttribute) {

        String tsid = timeSeriesDescription.toString();
        final String stdTextIdMask;
        if (standardTextId != null) {
            stdTextIdMask = standardTextId.getId();
        } else {
            stdTextIdMask = null;
        }
        TimeZone timeZone = OracleTypeMap.GMT_TIME_ZONE;
        String officeId = timeSeriesDescription.getOfficeId();

        return connectionResult(dsl, c -> {
            CwmsDbText dbText = CwmsDbServiceLookup.buildCwmsDb(CwmsDbText.class, c);
            ResultSet retrieveTsStdTextF = dbText.retrieveTsStdTextF(c, tsid,
                    stdTextIdMask, startTime, endTime, versionDate, timeZone, maxVersion,
                    retrieveText, minAttribute, maxAttribute, officeId);

            return parseTimeSeriesStandardTextResultSet(timeSeriesDescription, retrieveTsStdTextF);
        });

    }

    private TextTimeSeries<StandardTextTimeSeriesRow> parseTimeSeriesStandardTextResultSet(
            ITimeSeriesDescription timeSeriesDescription, ResultSet rs) throws SQLException {
        OracleTypeMap.checkMetaData(rs.getMetaData(), timeSeriesStdTextColumnsList, "Standard "
                + "Text Time Series");

        TextTimeSeries.Builder<StandardTextTimeSeriesRow> builder = new TextTimeSeries.Builder<>();
        builder.withId(timeSeriesDescription.getTimeSeriesId());
        builder.withOfficeId(timeSeriesDescription.getOfficeId());

        while (rs.next()) {
            StandardTextTimeSeriesRow row = buildStandardTextTimeSeriesRow(rs,
                    timeSeriesDescription.getOfficeId());
            builder.withRow(row);
        }
        return builder.build();

    }

    private static StandardTextTimeSeriesRow buildStandardTextTimeSeriesRow(ResultSet rs,
                                                                            String officeId) throws SQLException {
        StandardTextTimeSeriesRow.Builder builder = new StandardTextTimeSeriesRow.Builder();
        Timestamp tsDateTime = rs.getTimestamp(DATE_TIME,
                OracleTypeMap.getInstance().getGmtCalendar());
        if (!rs.wasNull()) {
            Date dateTime = new Date(tsDateTime.getTime());
            builder.withDateTime(dateTime);
        }

        Timestamp tsVersionDate = rs.getTimestamp(VERSION_DATE);
        if (!rs.wasNull()) {
            Date versionDate = new Date(tsVersionDate.getTime());
            builder.withVersionDate(versionDate);
        }
        Timestamp tsDataEntryDate = rs.getTimestamp(DATA_ENTRY_DATE,
                OracleTypeMap.getInstance().getGmtCalendar());
        if (!rs.wasNull()) {
            Date dataEntryDate = new Date(tsDataEntryDate.getTime());
            builder.withDataEntryDate(dataEntryDate);
        }
        String stdTextId = rs.getString(STD_TEXT_ID);
        StandardTextId standardTextId = null;
        if (!rs.wasNull()) {
            standardTextId =
                    new StandardTextId.Builder().withOfficeId(officeId).withId(stdTextId).build();
            builder.withStandardTextId(standardTextId);
        }
        Number attribute = rs.getLong(ATTRIBUTE);
        if (!rs.wasNull()) {
            builder.withAttribute(attribute.longValue());
        }
        String clobString = rs.getString(STD_TEXT);
        if (!rs.wasNull() && standardTextId != null) {
            StandardTextValue standardTextValue =
                    new StandardTextValue.Builder().withId(standardTextId).withStandardText(clobString).build();
            builder.withStandardTextValue(standardTextValue);
        }
        return builder.build();
    }

    public void storeTimeSeriesText(TextTimeSeries<RegularTextTimeSeriesRow> textTimeSeries,
                                    boolean maxVersion, boolean replaceAll) {

        NavigableMap<DateDateKey, RegularTextTimeSeriesRow> textTimeSeriesMap =
                textTimeSeries.getTextTimeSeriesMap();
        Set<Map.Entry<DateDateKey, RegularTextTimeSeriesRow>> entrySet =
                textTimeSeriesMap.entrySet();
        for (Map.Entry<DateDateKey, RegularTextTimeSeriesRow> entry : entrySet) {
            RegularTextTimeSeriesRow regularTextTimeSeriesRow = entry.getValue();
            storeRow(textTimeSeries, regularTextTimeSeriesRow, maxVersion, replaceAll);
        }

    }

    private void storeRow(TextTimeSeries<RegularTextTimeSeriesRow> textTimeSeries,
                          RegularTextTimeSeriesRow regularTextTimeSeriesRow, boolean maxVersion,
                          boolean replaceAll) {

        String tsid = textTimeSeries.getId();
        TimeZone timeZone = OracleTypeMap.GMT_TIME_ZONE;
        String officeId = textTimeSeries.getOfficeId();

        String textId = regularTextTimeSeriesRow.getTextId();
        String textValue = regularTextTimeSeriesRow.getTextValue();
        Date dateTime = regularTextTimeSeriesRow.getDateTime();
        Date versionDate = regularTextTimeSeriesRow.getVersionDate();
        Long attribute = regularTextTimeSeriesRow.getAttribute();

        NavigableSet<Date> dates = new TreeSet<>();
        dates.add(dateTime);

        connection(dsl, connection -> {
            CwmsDbText dbText = CwmsDbServiceLookup.buildCwmsDb(CwmsDbText.class, connection);
            if (textId == null) {
                dbText.storeTsText(connection, tsid, textValue, dates,
                        versionDate, timeZone, maxVersion, replaceAll,
                        attribute, officeId);
            } else {
                dbText.storeTsTextId(connection, tsid, textId, dates,
                        versionDate, timeZone, maxVersion, replaceAll,
                        attribute, officeId);
            }
        });
    }

    public Collection<TextTimeSeries> retrieveFromView(String officeId, String tsId,
                                                       Date startTime, Date endTime,
                                                       Date versionDate, Long minAttribute,
                                                       Long maxAttribute

    ) {
        // get jooq field by name b/c this view isn't "jooqified"
        Field<String> officeIdField = DSL.field("OFFICE_ID", String.class);
        Field<String> tsIdField = DSL.field("CWMS_TS_ID", String.class);
        Field<Timestamp> dateTimeUTCField = DSL.field("DATE_TIME_UTC", Timestamp.class);
        Field<Timestamp> versionDateUTCField = DSL.field("VERSION_DATE_UTC", Timestamp.class);
        Field<Timestamp> dataEntryDateUTCField = DSL.field("DATA_ENTRY_DATE_UTC", Timestamp.class);
        Field<Integer> attrField = DSL.field("ATTRIBUTE", Integer.class);
        Field<String> stdTextIdField = DSL.field("STD_TEXT_ID", String.class);
        Field<String> textValueField = DSL.field("TEXT_VALUE", String.class);

        List<Field> fields = new ArrayList<>();
        fields.add(officeIdField);
        fields.add(tsIdField);
        fields.add(dateTimeUTCField);
        fields.add(versionDateUTCField);
        fields.add(dataEntryDateUTCField);
        fields.add(attrField);
        fields.add(stdTextIdField);
        fields.add(textValueField);

        // revisit these and see what other parameters we need/want to add.
        Condition where = DSL.noCondition();
        if(officeId != null){
            where = where.and(officeIdField.eq(officeId));
        }

        if(tsId != null){
            where = where.and(tsIdField.eq(tsId));
        }

        if(startTime != null){
            where = where.and(dateTimeUTCField.ge(new Timestamp(startTime.getTime())));
        }

        if(endTime != null){
            where = where.and(dateTimeUTCField.le(new Timestamp(endTime.getTime())));
        }

        if(versionDate != null){
            where = where.and(versionDateUTCField.eq(new Timestamp(versionDate.getTime())));
        }

        if(minAttribute != null){
            where = where.and(attrField.ge(Math.toIntExact(minAttribute)));
        }

        if(maxAttribute != null){
            where = where.and(attrField.le(Math.toIntExact(maxAttribute)));
        }


        ResultQuery<Record> query = dsl.select(fields)
                .from("CWMS_20.AV_TS_TEXT")
                .where(where)
               ;

        Map<Pair<String, String>, List<TextTimeSeriesRow>> found = query
                .collect(Collectors.groupingBy(TimeSeriesTextDao::buildPair,
                        LinkedHashMap::new, Collectors.mapping(this::buildRow, Collectors.toList())));

        List<TextTimeSeries> retval = null;
        if(! found.isEmpty()){
            retval = new ArrayList<>();

            for (Map.Entry<Pair<String, String>, List<TextTimeSeriesRow>> entry : found.entrySet()) {
                Pair<String, String> officeTsId = entry.getKey();

                TextTimeSeries tts = new TextTimeSeries.Builder()
                        .withOfficeId(officeTsId.getFirst())
                        .withId(officeTsId.getSecond())
                        .withRows(entry.getValue())
                        .build();
                retval.add(tts);
            }

        }

        return retval;
    }

    @NotNull
    private static Pair<String, String> buildPair(Record r) {
        Field<String> officeIdField = DSL.field("OFFICE_ID", String.class);
        Field<String> tsIdField = DSL.field("CWMS_TS_ID", String.class);
        return new Pair<>(r.get(officeIdField), r.get(tsIdField));
    }

    private TextTimeSeriesRow buildRow(Record next){
        Field<String> officeIdField = DSL.field("OFFICE_ID", String.class);
//        Field<String> tsIdField = DSL.field("CWMS_TS_ID", String.class);
        Field<Timestamp> dateTimeUTCField = DSL.field("DATE_TIME_UTC", Timestamp.class);
        Field<Timestamp> versionDateUTCField = DSL.field("VERSION_DATE_UTC", Timestamp.class);
        Field<Timestamp> dataEntryDateUTCField = DSL.field("DATA_ENTRY_DATE_UTC", Timestamp.class);
        Field<Integer> attributeField = DSL.field("ATTRIBUTE", Integer.class);
        Field<String> stdTextIdField = DSL.field("STD_TEXT_ID", String.class);
        Field<String> textValueField = DSL.field("TEXT_VALUE", String.class);

        String officeId = next.get(officeIdField);
//        String tsId = next.get(tsIdField);
        Timestamp dateTimeUTC = next.get(dateTimeUTCField);
        Timestamp versionDateUTC = next.get(versionDateUTCField);
        Timestamp dataEntryDateUTC = next.get(dataEntryDateUTCField);
        Integer attribute = next.get(attributeField);
        String stdTextId = next.get(stdTextIdField);
        String textValue = next.get(textValueField);

        if(stdTextId == null){
            RegularTextTimeSeriesRow.Builder builder = new RegularTextTimeSeriesRow.Builder();
            return builder
                    .withDateTime(new Date(dateTimeUTC.getTime()))
                    .withVersionDate(new Date(versionDateUTC.getTime()))
                    .withDataEntryDate(new Date(dataEntryDateUTC.getTime()))
                    .withAttribute(attribute)
                    .withTextValue(textValue)
                    .build();
        } else {
            StandardTextTimeSeriesRow.Builder builder = new StandardTextTimeSeriesRow.Builder();

            StandardTextId standardTextId = new StandardTextId.Builder()
                    .withOfficeId(officeId)
                    .withId(stdTextId)
                    .build();
            if (textValue == null) {
                builder.withStandardTextId(standardTextId);
            } else {
                builder.withStandardTextValue(new StandardTextValue.Builder()
                        .withId(standardTextId)
                        .withStandardText(textValue)
                        .build());
            }

            builder
                    .withDateTime(new Date(dateTimeUTC.getTime()))
                    .withVersionDate(new Date(versionDateUTC.getTime()))
                    .withDataEntryDate(new Date(dataEntryDateUTC.getTime()))
                .withAttribute(attribute);


            return builder.build();
        }

    }


}
