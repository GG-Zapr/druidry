/*
 * Copyright (c) 2017-present, Red Brick Lane Marketing Solutions Pvt. Ltd.
 * All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package query.aggregation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import in.zapr.druid.druidry.Context;
import in.zapr.druid.druidry.Interval;
import in.zapr.druid.druidry.aggregator.CountAggregator;
import in.zapr.druid.druidry.aggregator.DoubleSumAggregator;
import in.zapr.druid.druidry.aggregator.DruidAggregator;
import in.zapr.druid.druidry.aggregator.LongSumAggregator;
import in.zapr.druid.druidry.dimension.DruidDimension;
import in.zapr.druid.druidry.dimension.SimpleDimension;
import in.zapr.druid.druidry.filter.AndFilter;
import in.zapr.druid.druidry.filter.DruidFilter;
import in.zapr.druid.druidry.filter.OrFilter;
import in.zapr.druid.druidry.filter.SelectorFilter;
import in.zapr.druid.druidry.granularity.Granularity;
import in.zapr.druid.druidry.granularity.PredefinedGranularity;
import in.zapr.druid.druidry.granularity.SimpleGranularity;
import in.zapr.druid.druidry.limitSpec.DefaultLimitSpec;
import in.zapr.druid.druidry.limitSpec.orderByColumnSpec.OrderByColumnSpec;
import in.zapr.druid.druidry.limitSpec.orderByColumnSpec.OrderByColumnSpecString;
import in.zapr.druid.druidry.postAggregator.ArithmeticFunction;
import in.zapr.druid.druidry.postAggregator.ArithmeticPostAggregator;
import in.zapr.druid.druidry.postAggregator.ConstantPostAggregator;
import in.zapr.druid.druidry.postAggregator.DruidPostAggregator;
import in.zapr.druid.druidry.postAggregator.FieldAccessPostAggregator;
import in.zapr.druid.druidry.query.aggregation.DruidGroupByQuery;

public class GroupByTest {
    private static ObjectMapper objectMapper;

    @BeforeClass
    public void init() {
        objectMapper = new ObjectMapper();
    }

    @Test
    public void testSampleQuery() throws JsonProcessingException, JSONException {
        String expectedJsonAsString = "{\n" +
                "  \"queryType\": \"groupBy\",\n" +
                "  \"dataSource\": \"sample_datasource\",\n" +
                "  \"granularity\": \"day\",\n" +
                "  \"dimensions\": [\"country\", \"device\"],\n" +
                "  \"limitSpec\": { \"type\": \"default\", \"limit\": 5000, \"columns\": [\"country\", \"data_transfer\"] },\n" +
                "  \"filter\": {\n" +
                "    \"type\": \"and\",\n" +
                "    \"fields\": [\n" +
                "      { \"type\": \"selector\", \"dimension\": \"carrier\", \"value\": \"AT&T\" },\n" +
                "      { \"type\": \"or\", \n" +
                "        \"fields\": [\n" +
                "          { \"type\": \"selector\", \"dimension\": \"make\", \"value\": \"Apple\" },\n" +
                "          { \"type\": \"selector\", \"dimension\": \"make\", \"value\": \"Samsung\" }\n" +
                "        ]\n" +
                "      }\n" +
                "    ]\n" +
                "  },\n" +
                "  \"aggregations\": [\n" +
                "    { \"type\": \"longSum\", \"name\": \"total_usage\", \"fieldName\": \"user_count\" },\n" +
                "    { \"type\": \"doubleSum\", \"name\": \"data_transfer\", \"fieldName\": \"data_transfer\" }\n" +
                "  ],\n" +
                "  \"postAggregations\": [\n" +
                "    { \"type\": \"arithmetic\",\n" +
                "      \"name\": \"avg_usage\",\n" +
                "      \"fn\": \"/\",\n" +
                "      \"fields\": [\n" +
                "        { \"type\": \"fieldAccess\", \"fieldName\": \"data_transfer\" },\n" +
                "        { \"type\": \"fieldAccess\", \"fieldName\": \"total_usage\" }\n" +
                "      ]\n" +
                "    }\n" +
                "  ],\n" +
                "  \"intervals\": [ \"2012-01-01T00:00:00.000Z/2012-01-03T00:00:00.000Z\" ]\n" +
                "}\n";

        // Druid dimensions
        DruidDimension druidDimension1 = new SimpleDimension("country");
        DruidDimension druidDimension2 = new SimpleDimension("device");

        // Limit Spec
        List<OrderByColumnSpec> orderByColumnSpecs
                = Arrays.asList(new OrderByColumnSpecString("country"),
                new OrderByColumnSpecString("data_transfer"));
        DefaultLimitSpec limitSpec = new DefaultLimitSpec(5000, orderByColumnSpecs);

        // Filters
        DruidFilter carrierFilter = new SelectorFilter("carrier", "AT&T");
        DruidFilter samsungFilter = new SelectorFilter("make", "Apple");
        DruidFilter appleFilter = new SelectorFilter("make", "Samsung");

        DruidFilter makeFilter = new OrFilter(Arrays.asList(samsungFilter, appleFilter));
        DruidFilter filter = new AndFilter(Arrays.asList(carrierFilter, makeFilter));

        // Aggregations
        DruidAggregator usageAggregator = new LongSumAggregator("total_usage", "user_count");
        DruidAggregator transferAggregator = new DoubleSumAggregator("data_transfer", "data_transfer");

        // Post Aggregations
        DruidPostAggregator transferPostAggregator = new FieldAccessPostAggregator("total_usage");
        DruidPostAggregator usagePostAggregator = new FieldAccessPostAggregator("data_transfer");

        DruidPostAggregator postAggregator = ArithmeticPostAggregator.builder()
                .name("avg_usage")
                .function(ArithmeticFunction.DIVIDE)
                .fields(Arrays.asList(transferPostAggregator, usagePostAggregator))
                .build();

        // Interval
        DateTime startTime = new DateTime(2012, 1, 1, 0, 0, 0, DateTimeZone.UTC);
        DateTime endTime = new DateTime(2012, 1, 3, 0, 0, 0, DateTimeZone.UTC);
        Interval interval = new Interval(startTime, endTime);

        DruidGroupByQuery query = DruidGroupByQuery.builder()
                .dataSource("sample_datasource")
                .granularity(new SimpleGranularity(PredefinedGranularity.DAY))
                .dimensions(Arrays.asList(druidDimension1, druidDimension2))
                .limitSpec(limitSpec)
                .filter(filter)
                .aggregators(Arrays.asList(usageAggregator, transferAggregator))
                .postAggregators(Collections.singletonList(postAggregator))
                .intervals(Collections.singletonList(interval))
                .build();

        String actualJson = objectMapper.writeValueAsString(query);
        JSONAssert.assertEquals(actualJson, expectedJsonAsString, JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    public void testRequiredFields() throws JSONException, JsonProcessingException {
        DruidDimension druidDimension1 = new SimpleDimension("dim1");
        DruidDimension druidDimension2 = new SimpleDimension("dim2");

        Granularity granularity = new SimpleGranularity(PredefinedGranularity.ALL);
        // Interval
        DateTime startTime = new DateTime(2012, 1, 1, 0, 0, 0, DateTimeZone.UTC);
        DateTime endTime = new DateTime(2012, 1, 3, 0, 0, 0, DateTimeZone.UTC);
        Interval interval = new Interval(startTime, endTime);

        DruidGroupByQuery druidGroupByQuery = DruidGroupByQuery.builder()
                .dataSource("sample_datasource")
                .dimensions(Arrays.asList(druidDimension1, druidDimension2))
                .granularity(granularity)
                .intervals(Collections.singletonList(interval))
                .build();

        String actualJson = objectMapper.writeValueAsString(druidGroupByQuery);

        JSONObject expectedQuery = new JSONObject();
        expectedQuery.put("queryType", "groupBy");
        expectedQuery.put("dataSource", "sample_datasource");

        JSONArray intervalArray = new JSONArray(Collections.singletonList("2012-01-01T00:00:00.000Z/2012-01-03T00:00:00.000Z"));
        expectedQuery.put("intervals", intervalArray);
        expectedQuery.put("granularity", "all");
        JSONArray dimensionArray = new JSONArray(Arrays.asList("dim1","dim2"));
        expectedQuery.put("dimensions", dimensionArray);

        JSONAssert.assertEquals(actualJson, expectedQuery, JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    public void testAllFields() throws JSONException, JsonProcessingException {
        DruidDimension druidDimension1 = new SimpleDimension("dim1");
        DruidDimension druidDimension2 = new SimpleDimension("dim2");

        Granularity granularity = new SimpleGranularity(PredefinedGranularity.ALL);
        // Interval
        DateTime startTime = new DateTime(2012, 1, 1, 0, 0, 0, DateTimeZone.UTC);
        DateTime endTime = new DateTime(2012, 1, 3, 0, 0, 0, DateTimeZone.UTC);
        Interval interval = new Interval(startTime, endTime);

        DruidFilter filter = new SelectorFilter("Spread", "Peace");
        DruidAggregator aggregator = new CountAggregator("Chill");
        DruidPostAggregator postAggregator = new ConstantPostAggregator("Keep", 16.11);
        Context context = Context.builder()
                .populateCache(true)
                .build();

        DruidGroupByQuery druidGroupByQuery = DruidGroupByQuery.builder()
                .dataSource("sample_datasource")
                .dimensions(Arrays.asList(druidDimension1, druidDimension2))
                .granularity(granularity)
                .filter(filter)
                .aggregators(Collections.singletonList(aggregator))
                .postAggregators(Collections.singletonList(postAggregator))
                .intervals(Collections.singletonList(interval))
                .context(context)
                .build();

        String actualJson = objectMapper.writeValueAsString(druidGroupByQuery);

        JSONObject expectedFilter = new JSONObject();
        expectedFilter.put("type", "selector");
        expectedFilter.put("dimension", "Spread");
        expectedFilter.put("value", "Peace");

        JSONObject expectedAggregator = new JSONObject();
        expectedAggregator.put("type", "count");
        expectedAggregator.put("name", "Chill");

        JSONObject expectedPostAggregator = new JSONObject();
        expectedPostAggregator.put("type", "constant");
        expectedPostAggregator.put("name", "Keep");
        expectedPostAggregator.put("value", 16.11);

        JSONObject expectedContext = new JSONObject();
        expectedContext.put("populateCache", true);

        JSONArray intervalArray = new JSONArray(Collections.singletonList("2012-01-01T00:00:00.000Z/" +
                "2012-01-03T00:00:00.000Z"));
        JSONArray dimensionArray = new JSONArray(Arrays.asList("dim1","dim2"));

        JSONObject expectedQuery = new JSONObject();
        expectedQuery.put("queryType", "groupBy");
        expectedQuery.put("dataSource", "sample_datasource");
        expectedQuery.put("dimensions", dimensionArray);
        expectedQuery.put("intervals", intervalArray);
        expectedQuery.put("granularity", "all");
        expectedQuery.put("filter", expectedFilter);
        expectedQuery.put("aggregations", new JSONArray(Collections.singletonList(expectedAggregator)));
        expectedQuery.put("postAggregations", new JSONArray(Collections.singletonList(expectedPostAggregator)));
        expectedQuery.put("context", expectedContext);

        JSONAssert.assertEquals(actualJson, expectedQuery, JSONCompareMode.NON_EXTENSIBLE);
    }
}