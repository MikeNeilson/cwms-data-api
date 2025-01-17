package cwms.cda.data.dao.basin;

import cwms.cda.data.dao.JooqDao;
import cwms.cda.data.dto.CwmsId;
import cwms.cda.data.dto.basin.Basin;
import cwms.cda.api.enums.UnitSystem;
import cwms.cda.api.enums.Unit;
import org.jooq.impl.DSL;
import usace.cwms.db.dao.util.OracleTypeMap;
import usace.cwms.db.jooq.codegen.packages.CWMS_BASIN_PACKAGE;
import usace.cwms.db.jooq.codegen.packages.cwms_basin.RETRIEVE_BASIN;

import java.sql.SQLException;
import java.sql.ResultSet;
import java.util.List;
import java.util.ArrayList;

import org.jooq.DSLContext;

public class BasinDao extends JooqDao<Basin> {

    public BasinDao(DSLContext dsl) {
        super(dsl);
    }

    public List<Basin> getAllBasins(String unitSystem, String officeId) {
        return connectionResult(dsl, c -> {
            setOffice(c, officeId);
            String areaUnitIn = UnitSystem.EN.value().equals(unitSystem)
                    ? Unit.SQUARE_MILES.getValue() : Unit.SQUARE_KILOMETERS.getValue();
            try(ResultSet rs = CWMS_BASIN_PACKAGE.call_CAT_BASINS(DSL.using(c).configuration(), null, null, null, areaUnitIn, officeId).intoResultSet()) {
                return buildBasinsFromResultSet(rs, areaUnitIn);
            }
        });
    }

    public Basin getBasin(CwmsId basinId, String unitSystem) {
        return connectionResult(dsl, c -> {
            String areaUnitIn = UnitSystem.EN.value().equals(unitSystem)
                ? Unit.SQUARE_MILES.getValue() : Unit.SQUARE_KILOMETERS.getValue();
            String[] pParentBasinId = new String[1];
            Double[] pSortOrder = new Double[1];
            String[] pPrimaryStreamId = new String[1];
            Double[] pTotalDrainageArea = new Double[1];
            Double[] pContributingDrainageArea = new Double[1];
            String pBasinId = basinId.getName();
            String pOfficeId = basinId.getOfficeId();
            setOffice(c, pOfficeId);

            RETRIEVE_BASIN retrieveBasin = CWMS_BASIN_PACKAGE.call_RETRIEVE_BASIN(
                    DSL.using(c).configuration(), pBasinId, areaUnitIn, pOfficeId);
            pParentBasinId[0] = retrieveBasin.getP_PARENT_BASIN_ID();
            pSortOrder[0] = retrieveBasin.getP_SORT_ORDER();
            pPrimaryStreamId[0] = retrieveBasin.getP_PRIMARY_STREAM_ID();
            pTotalDrainageArea[0] = retrieveBasin.getP_TOTAL_DRAINAGE_AREA();
            pContributingDrainageArea[0] = retrieveBasin.getP_CONTRIBUTING_DRAINAGE_AREA();

            return new Basin.Builder()
                    .withBasinId(new CwmsId.Builder()
                            .withName(pBasinId)
                            .withOfficeId(pOfficeId)
                            .build())
                    .withTotalDrainageArea(pTotalDrainageArea[0])
                    .withContributingDrainageArea(pContributingDrainageArea[0])
                    .withParentBasinId(new CwmsId.Builder()
                            .withName(pParentBasinId[0]).
                            withOfficeId(pOfficeId)
                            .build())
                    .withSortOrder(pSortOrder[0])
                    .withAreaUnit(areaUnitIn)
                    .withPrimaryStreamId(new CwmsId.Builder()
                            .withName(pPrimaryStreamId[0])
                            .withOfficeId(pOfficeId)
                            .build())
                    .build();
        });
    }

    public void storeBasin(Basin basin) {
        basin.validate();

        connection(dsl, c -> {
            String basinId = basin.getBasinId().getName();
            String officeId = basin.getBasinId().getOfficeId();
            String parentBasinId = basin.getParentBasinId() != null ? basin.getParentBasinId().getName() : null;
            Double sortOrder = basin.getSortOrder();
            String primaryStreamId = basin.getPrimaryStreamId() != null ? basin.getPrimaryStreamId().getName() : null;
            Double totalDrainageArea = basin.getTotalDrainageArea();
            Double contributingDrainageArea = basin.getContributingDrainageArea();
            String areaUnit = basin.getAreaUnit();
            setOffice(c, officeId);
            CWMS_BASIN_PACKAGE.call_STORE_BASIN(DSL.using(c).configuration(), basinId, OracleTypeMap.formatBool(false), OracleTypeMap.formatBool(false),
                    parentBasinId, sortOrder, primaryStreamId, totalDrainageArea, contributingDrainageArea, areaUnit, officeId);
        });
    }

    public void renameBasin(CwmsId oldBasin, CwmsId newBasin) {
        newBasin.validate();

        connection(dsl, c -> {
            setOffice(c, oldBasin.getOfficeId());
            CWMS_BASIN_PACKAGE.call_RENAME_BASIN(DSL.using(c).configuration(), oldBasin.getName(), newBasin.getName(), oldBasin.getOfficeId());
        });

    }

    public void deleteBasin(CwmsId basinId, String deleteAction) {
        basinId.validate();

        connection(dsl, c -> {
            setOffice(c, basinId.getOfficeId());
            CWMS_BASIN_PACKAGE.call_DELETE_BASIN(DSL.using(c).configuration(), basinId.getName(), deleteAction, basinId.getOfficeId());
        });
    }

    private List<Basin> buildBasinsFromResultSet(ResultSet rs, String unitSystem) throws SQLException {
        List<Basin> basins = new ArrayList<>();
        while (rs.next()) {
            Basin basin = buildBasinFromRow(rs, unitSystem);
            basins.add(basin);
        }
        return basins;
    }

    private Basin buildBasinFromRow(ResultSet rs, String unitSystem) throws SQLException {
        String basinId = rs.getString("BASIN_ID");
        String officeId = rs.getString("OFFICE_ID");
        String parentBasinId = rs.getString("PARENT_BASIN_ID");
        Double sortOrder = rs.getDouble("SORT_ORDER");
        String primaryStreamId = rs.getString("PRIMARY_STREAM_ID");
        Double basinArea = rs.getDouble("TOTAL_DRAINAGE_AREA");
        Double contributingArea = rs.getDouble("CONTRIBUTING_DRAINAGE_AREA");
        return new Basin.Builder()
                .withBasinId(new CwmsId.Builder()
                        .withName(basinId)
                        .withOfficeId(officeId)
                        .build())
                .withTotalDrainageArea(basinArea)
                .withContributingDrainageArea(contributingArea)
                .withParentBasinId(new CwmsId.Builder()
                        .withName(parentBasinId)
                        .withOfficeId(officeId)
                        .build())
                .withSortOrder(sortOrder)
                .withAreaUnit(unitSystem)
                .withPrimaryStreamId(new CwmsId.Builder()
                        .withName(primaryStreamId)
                        .withOfficeId(officeId)
                        .build())
                .build();
    }

}
