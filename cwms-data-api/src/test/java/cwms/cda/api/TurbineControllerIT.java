/*
 * MIT License
 *
 * Copyright (c) 2024 Hydrologic Engineering Center
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package cwms.cda.api;

import cwms.cda.api.errors.NotFoundException;
import cwms.cda.data.dao.DeleteRule;
import cwms.cda.data.dao.JooqDao;
import cwms.cda.data.dao.LocationsDaoImpl;
import cwms.cda.data.dao.location.kind.LocationUtil;
import cwms.cda.data.dao.location.kind.TurbineDao;
import cwms.cda.data.dto.Location;
import cwms.cda.data.dto.location.kind.Turbine;
import cwms.cda.formatters.ContentType;
import cwms.cda.formatters.Formats;
import fixtures.CwmsDataApiSetupCallback;
import fixtures.TestAccounts;
import io.restassured.filter.log.LogDetail;
import mil.army.usace.hec.test.database.CwmsDatabaseContainer;
import org.apache.commons.io.IOUtils;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import usace.cwms.db.jooq.codegen.packages.CWMS_PROJECT_PACKAGE;
import usace.cwms.db.jooq.codegen.udt.records.PROJECT_OBJ_T;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;

import static cwms.cda.api.Controllers.FAIL_IF_EXISTS;
import static cwms.cda.data.dao.DaoTest.getDslContext;
import static cwms.cda.security.KeyAccessManager.AUTH_HEADER;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

//* NOTE requires at least 24.05.24-RC03 to fix nation ID issue with store_location_f */
final class TurbineControllerIT extends DataApiTestIT {
    private static final String OFFICE = TestAccounts.KeyUser.SWT_NORMAL.getOperatingOffice();
    private static final Location PROJECT_LOC;
    private static final Location TURBINE_LOC;
    private static final Turbine TURBINE;
    static {
        try(InputStream projectStream = TurbineControllerIT.class.getResourceAsStream("/cwms/cda/api/project_location.json");
            InputStream turbineStream = TurbineControllerIT.class.getResourceAsStream("/cwms/cda/api/turbine.json")) {
            String projectLocJson = IOUtils.toString(projectStream, StandardCharsets.UTF_8);
            PROJECT_LOC = Formats.parseContent(new ContentType(Formats.JSONV1), projectLocJson, Location.class);
            String turbineJson = IOUtils.toString(turbineStream, StandardCharsets.UTF_8);
            TURBINE = Formats.parseContent(new ContentType(Formats.JSONV1), turbineJson, Turbine.class);
            TURBINE_LOC = TURBINE.getLocation();
        } catch(Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @BeforeAll
    public static void setup() throws Exception {
        tearDown();
        CwmsDatabaseContainer<?> databaseLink = CwmsDataApiSetupCallback.getDatabaseLink();
        databaseLink.connection(c -> {
            try {
                DSLContext context = getDslContext(c, OFFICE);
                LocationsDaoImpl locationsDao = new LocationsDaoImpl(context);
                PROJECT_OBJ_T projectObjT = buildProject();
                CWMS_PROJECT_PACKAGE.call_STORE_PROJECT(context.configuration(), projectObjT, "T");
                locationsDao.storeLocation(TURBINE_LOC);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        },
        CwmsDataApiSetupCallback.getWebUser());
    }

    @AfterAll
    public static void tearDown() throws Exception {
        CwmsDatabaseContainer<?> databaseLink = CwmsDataApiSetupCallback.getDatabaseLink();
        databaseLink.connection(c -> {
            DSLContext context = getDslContext(c, OFFICE);
            cleanTurbine(context, TURBINE_LOC);
            cleanProject(context, PROJECT_LOC);
        },
        CwmsDataApiSetupCallback.getWebUser());
    }

    @Test
    void test_get_create_delete() {

        // Structure of test:
        // 1)Create the Turbine
        // 2)Retrieve the Turbine and assert that it exists
        // 3)Delete the Turbine
        // 4)Retrieve the Turbine and assert that it does not exist
        TestAccounts.KeyUser user = TestAccounts.KeyUser.SWT_NORMAL;
        String json = Formats.format(Formats.parseHeader(Formats.JSONV1, Turbine.class), TURBINE);
        //Create the Turbine
        given()
            .log().ifValidationFails(LogDetail.ALL, true)
            .contentType(Formats.JSONV1)
            .body(json)
            .header(AUTH_HEADER, user.toHeaderValue())
            .queryParam(FAIL_IF_EXISTS, "false")
        .when()
            .redirects().follow(true)
            .redirects().max(3)
            .post("projects/turbines/")
        .then()
            .log().ifValidationFails(LogDetail.ALL, true)
        .assertThat()
            .statusCode(is(HttpServletResponse.SC_CREATED))
        ;
        String office = TURBINE.getLocation().getOfficeId();
        // Retrieve the Turbine and assert that it exists
        given()
            .log().ifValidationFails(LogDetail.ALL,true)
            .accept(Formats.JSONV1)
            .queryParam(Controllers.OFFICE, office)
        .when()
            .redirects().follow(true)
            .redirects().max(3)
            .get("projects/turbines/" + TURBINE.getLocation().getName())
        .then()
            .log().ifValidationFails(LogDetail.ALL,true)
        .assertThat()
            .statusCode(is(HttpServletResponse.SC_OK))
            .body("location", not(nullValue()))
            .body("project-id.name", equalTo(TURBINE.getProjectId().getName()))
            .body("project-id.office-id", equalTo(TURBINE.getProjectId().getOfficeId()))
        ;

        // Delete a Turbine
        given()
            .log().ifValidationFails(LogDetail.ALL,true)
            .queryParam(Controllers.OFFICE, office)
            .header(AUTH_HEADER, user.toHeaderValue())
        .when()
            .redirects().follow(true)
            .redirects().max(3)
            .delete("projects/turbines/" + TURBINE.getLocation().getName())
        .then()
            .log().ifValidationFails(LogDetail.ALL,true)
        .assertThat()
            .statusCode(is(HttpServletResponse.SC_NO_CONTENT))
        ;

        // Retrieve a Turbine and assert that it does not exist
        given()
            .log().ifValidationFails(LogDetail.ALL,true)
            .accept(Formats.JSONV1)
            .queryParam(Controllers.OFFICE, office)
        .when()
            .redirects().follow(true)
            .redirects().max(3)
            .get("projects/turbines/" + TURBINE.getLocation().getName())
        .then()
            .log().ifValidationFails(LogDetail.ALL,true)
        .assertThat()
            .statusCode(is(HttpServletResponse.SC_NOT_FOUND))
        ;
    }

    @Test
    void test_update_does_not_exist() {
        TestAccounts.KeyUser user = TestAccounts.KeyUser.SWT_NORMAL;
        //Create the Turbine
        given()
            .log().ifValidationFails(LogDetail.ALL, true)
            .contentType(Formats.JSONV1)
            .queryParam(Controllers.OFFICE, user.getOperatingOffice())
            .queryParam(Controllers.NAME, "NewBogus")
            .header(AUTH_HEADER, user.toHeaderValue())
        .when()
            .redirects().follow(true)
            .redirects().max(3)
            .patch("projects/turbines/bogus")
        .then()
            .log().ifValidationFails(LogDetail.ALL, true)
        .assertThat()
            .statusCode(is(HttpServletResponse.SC_NOT_FOUND))
        ;
    }

    @Test
    void test_delete_does_not_exist() {
        TestAccounts.KeyUser user = TestAccounts.KeyUser.SWT_NORMAL;
        // Delete a Turbine
        given()
            .log().ifValidationFails(LogDetail.ALL,true)
            .queryParam(Controllers.OFFICE, user.getOperatingOffice())
            .header(AUTH_HEADER, user.toHeaderValue())
        .when()
            .redirects().follow(true)
            .redirects().max(3)
            .delete("projects/turbines/" + Instant.now().toEpochMilli())
        .then()
            .log().ifValidationFails(LogDetail.ALL,true)
        .assertThat()
            .statusCode(is(HttpServletResponse.SC_NOT_FOUND))
        ;
    }

    @Test
    void test_get_all() {

        // Structure of test:
        // 1)Create the Turbine
        // 2)Retrieve the Turbine with getAll and assert that it exists
        // 3)Delete the Turbine
        TestAccounts.KeyUser user = TestAccounts.KeyUser.SWT_NORMAL;
        String json = Formats.format(Formats.parseHeader(Formats.JSON, Turbine.class), TURBINE);
        //Create the Turbine
        given()
            .log().ifValidationFails(LogDetail.ALL, true)
            .contentType(Formats.JSONV1)
            .body(json)
            .header(AUTH_HEADER, user.toHeaderValue())
            .queryParam(FAIL_IF_EXISTS, "false")
        .when()
            .redirects().follow(true)
            .redirects().max(3)
            .post("projects/turbines/")
        .then()
            .log().ifValidationFails(LogDetail.ALL, true)
        .assertThat()
            .statusCode(is(HttpServletResponse.SC_CREATED))
        ;
        String office = TURBINE.getLocation().getOfficeId();
        // Retrieve the Turbine and assert that it exists
        given()
            .log().ifValidationFails(LogDetail.ALL,true)
            .accept(Formats.JSONV1)
            .queryParam(Controllers.OFFICE, office)
            .queryParam(Controllers.PROJECT_ID, TURBINE.getProjectId().getName())
        .when()
            .redirects().follow(true)
            .redirects().max(3)
            .get("projects/turbines/")
        .then()
            .log().ifValidationFails(LogDetail.ALL,true)
        .assertThat()
            .statusCode(is(HttpServletResponse.SC_OK))
            .body("[0].location", not(nullValue()))
            .body("[0].project-id.name", equalTo(TURBINE.getProjectId().getName()))
            .body("[0].project-id.office-id", equalTo(TURBINE.getProjectId().getOfficeId()))
        ;

        // Delete a Turbine
        given()
            .log().ifValidationFails(LogDetail.ALL,true)
            .queryParam(Controllers.OFFICE, office)
            .header(AUTH_HEADER, user.toHeaderValue())
        .when()
            .redirects().follow(true)
            .redirects().max(3)
            .delete("projects/turbines/" + TURBINE.getLocation().getName())
        .then()
            .log().ifValidationFails(LogDetail.ALL,true)
        .assertThat()
            .statusCode(is(HttpServletResponse.SC_NO_CONTENT))
        ;
    }

    private static PROJECT_OBJ_T buildProject() {
        PROJECT_OBJ_T retval = new PROJECT_OBJ_T();
        retval.setPROJECT_LOCATION(LocationUtil.getLocation(PROJECT_LOC));
        retval.setPUMP_BACK_LOCATION(null);
        retval.setNEAR_GAGE_LOCATION(null);
        retval.setAUTHORIZING_LAW(null);
        retval.setCOST_YEAR(Timestamp.from(Instant.now()));
        retval.setFEDERAL_COST(BigDecimal.ONE);
        retval.setNONFEDERAL_COST(BigDecimal.TEN);
        retval.setFEDERAL_OM_COST(BigDecimal.ZERO);
        retval.setNONFEDERAL_OM_COST(BigDecimal.valueOf(15.0));
        retval.setCOST_UNITS_ID("$");
        retval.setREMARKS("TEST RESERVOIR PROJECT");
        retval.setPROJECT_OWNER("CDA");
        retval.setHYDROPOWER_DESCRIPTION("HYDRO DESCRIPTION");
        retval.setSEDIMENTATION_DESCRIPTION("SEDIMENTATION DESCRIPTION");
        retval.setDOWNSTREAM_URBAN_DESCRIPTION("DOWNSTREAM URBAN DESCRIPTION");
        retval.setBANK_FULL_CAPACITY_DESCRIPTION("BANK FULL CAPACITY DESCRIPTION");
        retval.setYIELD_TIME_FRAME_START(Timestamp.from(Instant.now()));
        retval.setYIELD_TIME_FRAME_END(Timestamp.from(Instant.now()));
        return retval;
    }

    private static void cleanTurbine(DSLContext context, Location turbine) {
        try {
            new TurbineDao(context).deleteTurbine(turbine.getName(), OFFICE, DeleteRule.DELETE_ALL);
        } catch (NotFoundException ex) {
            /* this is only an error within the tests themselves */
        }

        try {
            new LocationsDaoImpl(context).deleteLocation(turbine.getName(), OFFICE, true);
        } catch (NotFoundException ex) {
            /* this is only an error within the tests themselves */
        }
    }

    private static void cleanProject(DSLContext context, Location project) {
        try {
            CWMS_PROJECT_PACKAGE.call_DELETE_PROJECT(context.configuration(), project.getName(),
                DeleteRule.DELETE_ALL.getRule(), OFFICE);
        } catch (DataAccessException ex) {
            if (!JooqDao.isNotFound(ex)) {
                throw ex;
            }
        } catch (NotFoundException ex) {
            /* this is only an error within the tests themselves */
        }

        try {
            new LocationsDaoImpl(context).deleteLocation(project.getName(), OFFICE, true);
        } catch (NotFoundException ex) {
            /* this is only an error within the tests themselves */
        }
    }
}