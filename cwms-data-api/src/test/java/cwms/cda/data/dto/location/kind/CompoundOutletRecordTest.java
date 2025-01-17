/*
 * MIT License
 * Copyright (c) 2024 Hydrologic Engineering Center
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package cwms.cda.data.dto.location.kind;

import cwms.cda.data.dto.CwmsId;
import cwms.cda.formatters.ContentType;
import cwms.cda.formatters.Formats;
import cwms.cda.formatters.json.JsonV2;
import cwms.cda.helpers.DTOMatch;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CompoundOutletRecordTest {
    private static final String SPK = "SPK";
    @Test
    void test_serialization() {
        ContentType contentType = Formats.parseHeader(Formats.JSON, Outlet.class);
        CompoundOutletRecord data = buildTestData();
        String json = Formats.format(contentType, data);

        CompoundOutletRecord deserialized = Formats.parseContent(contentType, json, CompoundOutletRecord.class);
        DTOMatch.assertMatch(data, deserialized);
    }

    @Test
    void test_serialize_from_file() throws Exception {
        ContentType contentType = Formats.parseHeader(Formats.JSON, Outlet.class);
        CompoundOutletRecord data = buildTestData();
        InputStream resource = this.getClass()
                                   .getResourceAsStream("/cwms/cda/data/dto/location/kind/compound_outlet_record.json");
        assertNotNull(resource);
        String serialized = IOUtils.toString(resource, StandardCharsets.UTF_8);
        CompoundOutletRecord deserialized = Formats.parseContent(contentType, serialized, CompoundOutletRecord.class);
        DTOMatch.assertMatch(data, deserialized);
    }

    private CompoundOutletRecord buildTestData() {
        return new CompoundOutletRecord.Builder().withOutletId(new CwmsId.Builder().withName("TG01").withOfficeId(SPK).build())
                                                 .withDownstreamOutletIds(Arrays.asList(
                                                         new CwmsId.Builder().withName("TG02")
                                                                             .withOfficeId(SPK)
                                                                             .build(),
                                                         new CwmsId.Builder().withName("TG03")
                                                                             .withOfficeId(SPK)
                                                                             .build()))
                                                 .build();
    }
}