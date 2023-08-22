package org.upsmf.grievance.service.impl;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.upsmf.grievance.config.EsConfig;
import org.upsmf.grievance.constants.Constants;
import org.upsmf.grievance.dto.SearchRequest;
import org.upsmf.grievance.enums.RequesterType;
import org.upsmf.grievance.enums.TicketPriority;
import org.upsmf.grievance.enums.TicketStatus;
import org.upsmf.grievance.model.es.Ticket;
import org.upsmf.grievance.model.reponse.TicketResponse;
import org.upsmf.grievance.repository.es.TicketRepository;
import org.upsmf.grievance.service.SearchService;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class SearchServiceImpl implements SearchService {

    @Value("${es.default.page.size}")
    private int defaultPageSize;

    @Value("${pending.21.days}")
    private long PENDING_21_DAYS;

    @Value("${pending.15.days}")
    private long PENDING_15_DAYS;

    private Map<String, Object> departmentNameResponse = new HashMap<>();
    private Map<String, Object> performanceIndicatorsResponse = new HashMap<>();
    private Map<String, Object> finalResponse = new HashMap<>();
    private Boolean allDepartment = false;
    private Boolean totalFinalResponse = false;
    private Boolean multiSelectResponse = false;
    private long totalIsJunk = 0;
    private long totalOpenStatus = 0;
    private long totalcloseStatus = 0;
    private long totalIsEscalated = 0;
    private long totalUnassigned = 0;
    private long totalNudgeTickets = 0;
    private long totalOpenTicketGte15 = 0;
    private long totalOpenTicketGte21 = 0;
    @Autowired
    private TicketRepository esTicketRepository;
    @Autowired
    private EsConfig esConfig;

    @Override
    public TicketResponse search(SearchRequest searchRequest) {
        //Calculate
        String keyValue = searchRequest.getSort().keySet().iterator().next();
        Pageable pageable = PageRequest.of(searchRequest.getPage(), searchRequest.getSize(), Sort.Direction.valueOf(searchRequest.getSort().get(keyValue).toUpperCase()), keyValue);
        Page<Ticket> page = esTicketRepository.findAll(pageable);
        return TicketResponse.builder().count(page.getTotalElements()).data(page.getContent()).build();
    }

    @Override
    public Map<String, Object> dashboardReport(SearchRequest searchRequest) {
        //Create query for search by keyword
        departmentNameResponse = new HashMap<>();
        performanceIndicatorsResponse = new HashMap<>();
        finalResponse = new HashMap<>();
        totalIsJunk = 0;
        totalOpenStatus = 0;
        totalcloseStatus = 0;
        totalIsEscalated = 0;
        totalUnassigned = 0;
        totalOpenTicketGte21 = 0;
        totalOpenTicketGte15 = 0;
        totalNudgeTickets = 0;
        allDepartment = false;
        totalFinalResponse = false;
        multiSelectResponse = false;
        if (searchRequest.getFilter() != null) {
            List<Integer> ccList = (List<Integer>) searchRequest.getFilter().get("ccList");
            if (ccList != null && ccList.size() > 0) {
                for (int i = 1; i <= ccList.size(); i++) {
                    if (ccList.size() == i) {
                        multiSelectResponse = true;
                    }
                    Long cc = Long.valueOf(ccList.get(i - 1));
                    if (cc != null && cc.equals(Constants.AFFILIATION)) {
                        getfinalResponse(searchRequest, Constants.AFFILIATION);
                    } else if (cc != null && cc.equals(Constants.EXAM)) {
                        getfinalResponse(searchRequest, Constants.EXAM);
                    } else if (cc != null && cc.equals(Constants.ADMISSION)) {
                        getfinalResponse(searchRequest, Constants.ADMISSION);
                    } else if (cc != null && cc.equals(Constants.REGISTRATION)) {
                        getfinalResponse(searchRequest, Constants.REGISTRATION);
                    } else if (cc != null && cc.equals(Constants.ASSESSMENT)) {
                        getfinalResponse(searchRequest, Constants.ASSESSMENT);
                    } else {
                        allDepartment = true;
                        getfinalResponse(searchRequest, Constants.AFFILIATION);
                        getfinalResponse(searchRequest, Constants.EXAM);
                        getfinalResponse(searchRequest, Constants.ADMISSION);
                        getfinalResponse(searchRequest, Constants.REGISTRATION);
                        totalFinalResponse = true;// This flag should be there before last getfinalResponse
                        getfinalResponse(searchRequest, Constants.ASSESSMENT);
                    }
                }
            } else {
                allDepartment = true;
                getfinalResponse(searchRequest, Constants.AFFILIATION);
                getfinalResponse(searchRequest, Constants.EXAM);
                getfinalResponse(searchRequest, Constants.ADMISSION);
                getfinalResponse(searchRequest, Constants.REGISTRATION);
                totalFinalResponse = true;// This flag should be there before last getfinalResponse
                getfinalResponse(searchRequest, Constants.ASSESSMENT);
            }
        } else {
            allDepartment = true;
            getfinalResponse(searchRequest, Constants.AFFILIATION);
            getfinalResponse(searchRequest, Constants.EXAM);
            getfinalResponse(searchRequest, Constants.ADMISSION);
            getfinalResponse(searchRequest, Constants.REGISTRATION);
            totalFinalResponse = true;// This flag should be there before last getfinalResponse
            getfinalResponse(searchRequest, Constants.ASSESSMENT);
        }
        return finalResponse;
    }

    private void getfinalResponse(SearchRequest searchRequest, Long cc) {
        SearchResponse searchJunkResponse = getDashboardSearchResponse(searchRequest, "isJunk", cc, null);
        SearchResponse searchOpenStatusResponse = getDashboardSearchResponse(searchRequest, "openStatus", cc, null);
        SearchResponse searchClosedStatusResponse = getDashboardSearchResponse(searchRequest, "closedStatus", cc, null);
        SearchResponse searchTurnAroundStatusResponse = getDashboardSearchResponse(searchRequest, "closedStatus", cc, null);
        SearchResponse searchIsEsclatedResponse = getDashboardSearchResponse(searchRequest, "isEscalated", cc, null);
        SearchResponse searchUnassignedResponse = getDashboardSearchResponse(searchRequest, "openStatus", -1l, null);
        SearchResponse searchOpenTicketsGte21Response = getDashboardSearchResponse(searchRequest, "openStatus", cc, "isGte21");
        SearchResponse searchOpenTicketsGte15Response = getDashboardSearchResponse(searchRequest, "openStatus", cc, "isGte15");
        SearchResponse searchPriorityResponse = getDashboardSearchResponse(searchRequest, "highPriority", cc, null);

        Map<String, Object> response = new HashMap<>();
        totalIsJunk = totalIsJunk + searchJunkResponse.getHits().getTotalHits().value;
        totalOpenStatus = totalOpenStatus + searchOpenStatusResponse.getHits().getTotalHits().value;
        totalcloseStatus = totalcloseStatus + searchClosedStatusResponse.getHits().getTotalHits().value;
        totalIsEscalated = totalIsEscalated + searchIsEsclatedResponse.getHits().getTotalHits().value;
        totalUnassigned = totalUnassigned + searchUnassignedResponse.getHits().getTotalHits().value;
        totalNudgeTickets = totalNudgeTickets + searchPriorityResponse.getHits().getTotalHits().value;
        totalOpenTicketGte21 = totalOpenTicketGte21 + searchOpenTicketsGte21Response.getHits().getTotalHits().value;
        long totalTicketsCount = searchJunkResponse.getHits().getTotalHits().value + searchOpenStatusResponse.getHits().getTotalHits().value
                + searchClosedStatusResponse.getHits().getTotalHits().value + searchIsEsclatedResponse.getHits().getTotalHits().value
                + searchOpenTicketsGte15Response.getHits().getTotalHits().value + searchUnassignedResponse.getHits().getTotalHits().value;
        response.put(Constants.TOTAL_TICKETS, totalTicketsCount);
        response.put(Constants.IS_JUNK, searchJunkResponse.getHits().getTotalHits().value);
        response.put(Constants.IS_OPEN, searchOpenStatusResponse.getHits().getTotalHits().value);
        response.put(Constants.IS_CLOSED, searchClosedStatusResponse.getHits().getTotalHits().value);
        response.put(Constants.IS_ESCALATED, searchIsEsclatedResponse.getHits().getTotalHits().value);
        response.put(Constants.UNASSIGNED, searchUnassignedResponse.getHits().getTotalHits().value);
        response.put(Constants.OPEN_TICKET_GTE15, searchOpenTicketsGte15Response.getHits().getTotalHits().value);

        if (cc.equals(Constants.ASSESSMENT)) {
            departmentNameResponse.put(Constants.ASSESSMENT_DEPARTMENT, response);
        } else if (cc.equals(Constants.AFFILIATION)) {
            departmentNameResponse.put(Constants.AFFILIATION_NAME, response);
        } else if (cc.equals(Constants.EXAM)) {
            departmentNameResponse.put(Constants.EXAM_NAME, response);
        } else if (cc.equals(Constants.REGISTRATION)) {
            departmentNameResponse.put(Constants.REGISTRATION_NAME, response);
        } else if (cc.equals(Constants.ADMISSION)) {
            departmentNameResponse.put(Constants.ADMISSION_NAME, response);
        }
        if (totalFinalResponse) {
            getResponse();
        }
        if (multiSelectResponse) {
            getResponse();
        }
        finalResponse.put(Constants.RESOLUTION_MATRIX, departmentNameResponse);
    }

    private void getResponse() {
        Map<String, Object> response = new HashMap<>();
        long totalTicketsCount = totalIsJunk + totalIsEscalated + totalOpenStatus + totalcloseStatus + totalUnassigned;
        response.put(Constants.TOTAL_TICKETS, totalTicketsCount);
        response.put(Constants.IS_JUNK, totalIsJunk);
        response.put(Constants.IS_OPEN, totalOpenStatus);
        response.put(Constants.IS_CLOSED, totalcloseStatus);
        response.put(Constants.IS_ESCALATED, totalIsEscalated);
        response.put(Constants.UNASSIGNED, totalUnassigned);
        finalResponse.put(Constants.ASSESSMENT_MATRIX, response);

        performanceIndicatorsResponse = new HashMap<>();
        performanceIndicatorsResponse.put(Constants.TURN_AROUND_TIME, 0 + " days");
        int esclationPercentage = 0;
        int nudgePercentage = 0;
        if(totalTicketsCount != 0){
            esclationPercentage = (int) Math.round((double) (totalIsEscalated / totalTicketsCount) * 100);
            nudgePercentage = (int) Math.round((double) (totalNudgeTickets) / totalTicketsCount * 100);
        }
        performanceIndicatorsResponse.put(Constants.ESCLATION_PERCENTAGE, esclationPercentage + "%");
        performanceIndicatorsResponse.put(Constants.NUDGE_TICKET_PERCENTAGE, nudgePercentage + "%");
        performanceIndicatorsResponse.put(Constants.OPEN_TICKET_GTE21, totalOpenTicketGte21);
        finalResponse.put(Constants.PERFORMANCE_INDICATORS, performanceIndicatorsResponse);
    }

    private SearchResponse getDashboardSearchResponse(SearchRequest searchRequest, String reportType, Long cc, String flag) {
        SearchResponse searchResponse;
        BoolQueryBuilder finalQuery = QueryBuilders.boolQuery();
        if (flag != null && flag.equals("isGte21")) {
            finalQuery = getGte21DaysQuery(searchRequest, finalQuery);
        } else if (flag != null && flag.equals("isGte15")) {
            finalQuery = getGte15DaysQuery(searchRequest, finalQuery);
        } else {
            finalQuery = getDateRangeQuery(searchRequest, finalQuery);
        }
        finalQuery = getCCRangeQuery(cc, finalQuery);
        if (reportType.equals("isJunk")) {
            finalQuery = getJunkQuery(true, finalQuery);
        } else if (reportType.equals("openStatus")) {
            List<String> list = new ArrayList<>();
            list.add("OPEN");
            finalQuery = getStatusQuery(list, finalQuery);
        } else if (reportType.equals("closedStatus")) {
            List<String> list = new ArrayList<>();
            list.add("CLOSED");
            finalQuery = getStatusQuery(list, finalQuery);
            finalQuery = getJunkQuery(false, finalQuery);
        } else if (reportType.equals("isEscalated")) {
            finalQuery = getEsclatedTicketsQuery(true, finalQuery);
        } else if (reportType.equals("highPriority")) {
            finalQuery = getPriority("HIGH", finalQuery);
        }
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
                .query(finalQuery);

        org.elasticsearch.action.search.SearchRequest search = new org.elasticsearch.action.search.SearchRequest("ticket");
        search.searchType(SearchType.QUERY_THEN_FETCH);
        search.source(searchSourceBuilder);
        try {
            searchResponse = esConfig.elasticsearchClient().search(search, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return searchResponse;
    }

    @Override
    public Map<String, Object> searchTickets(SearchRequest searchRequest) {
        //Create query for search by keyword
        SearchResponse searchResponse = null;
        searchResponse = getSearchResponse(searchRequest);
        Map<String, Object> response = new HashMap<>();
        List<Object> results = getDocumentsFromSearchResult(searchResponse);
        response.put("count", searchResponse.getHits().getTotalHits().value);
        response.put("results", results);
        return response;
    }

    private SearchResponse getSearchResponse(SearchRequest searchRequest) {
        SearchResponse searchResponse;
        String keyValue = searchRequest.getSort().keySet().iterator().next();
        keyValue = getKeyValue(keyValue);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
                .query(createTicketSearchQuery(searchRequest))
                .from(searchRequest.getPage())
                .size(searchRequest.getSize())
                .sort(keyValue, SortOrder.valueOf(searchRequest.getSort().get(searchRequest.getSort().keySet().iterator().next()).toUpperCase()));

        org.elasticsearch.action.search.SearchRequest search = new org.elasticsearch.action.search.SearchRequest("ticket");
        search.searchType(SearchType.QUERY_THEN_FETCH);
        search.source(searchSourceBuilder);
        try {
            searchResponse = esConfig.elasticsearchClient().search(search, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return searchResponse;
    }

    private String getKeyValue(String keyValue) {
        switch (keyValue) {
            case "ticketId":
                keyValue = "ticket_id";
                break;
            case "firstName":
                keyValue = "requester_first_name";
                break;
            case "lastName":
                keyValue = "requester_last_name";
                break;
            case "phone":
                keyValue = "requester_phone";
                break;
            case "email":
                keyValue = "requester_email";
                break;
            case "requesterType":
                keyValue = "requester_type";
                break;
            case "assignedToId":
                keyValue = "assigned_to_id";
                break;
            case "assignedToName":
                keyValue = "assigned_to_name";
                break;
            case "description":
                keyValue = "description";
                break;
            case "junk":
                keyValue = "is_junk";
                break;
            case "createdDate":
                keyValue = "created_date";
                break;
            case "updatedDate":
                keyValue = "updated_date";
                break;
            case "createdDateTS":
                keyValue = "created_date_ts";
                break;
            case "updatedDateTS":
                keyValue = "updated_date_ts";
                break;
            case "lastUpdatedBy":
                keyValue = "last_updated_by";
                break;
            case "escalated":
                keyValue = "is_escalated";
                break;
            case "escalatedDate":
                keyValue = "escalated_date";
                break;
            case "escalatedDateTS":
                keyValue = "escalated_date_ts";
                break;
            case "escalatedTo":
                keyValue = "escalated_to";
                break;
            case "status":
                keyValue = "status";
                break;
            case "requestType":
                keyValue = "request_type";
                break;
            case "priority":
                keyValue = "priority";
                break;
            case "escalatedBy":
                keyValue = "escalated_by";
                break;
        }
        return keyValue;
    }

    private List<Object> getDocumentsFromSearchResult(SearchResponse result) {
        SearchHits hits = result.getHits();
        return getDocumentsFromHits(hits);
    }

    private List<Object> getDocumentsFromHits(SearchHits hits) {
        List<Object> documents = new ArrayList<Object>();
        for (SearchHit hit : hits) {
            Ticket esTicket = new Ticket();
            for (Map.Entry entry : hit.getSourceAsMap().entrySet()) {
                String key = (String) entry.getKey();
                mapEsTicketDtoToTicketDto(entry, key, esTicket);
            }
            documents.add(esTicket);
        }
        return documents;
    }

    private void mapEsTicketDtoToTicketDto(Map.Entry entry, String key, Ticket esTicket) {
        switch (key) {
            case "ticket_id":
                Long longValue = ((Number) entry.getValue()).longValue();
                esTicket.setTicketId(longValue);
                break;
            case "requester_first_name":
                esTicket.setFirstName((String) entry.getValue());
                break;
            case "requester_last_name":
                esTicket.setLastName((String) entry.getValue());
                break;
            case "requester_phone":
                esTicket.setPhone((String) entry.getValue());
                break;
            case "requester_email":
                esTicket.setEmail((String) entry.getValue());
                break;
            case "requester_type":
                for (RequesterType enumValue : RequesterType.values()) {
                    if (enumValue.name().equals(entry.getValue().toString())) {
                        esTicket.setRequesterType(enumValue);
                        break;
                    }
                }
                break;
            case "assigned_to_id":
                longValue = ((Number) entry.getValue()).longValue();
                esTicket.setAssignedToId(longValue);
                break;
            case "assigned_to_name":
                esTicket.setAssignedToName((String) entry.getValue());
                break;
            case "description":
                esTicket.setDescription((String) entry.getValue());
                break;
            case "is_junk":
                esTicket.setJunk((Boolean) entry.getValue());
                break;
            case "created_date":
                esTicket.setCreatedDate((String) entry.getValue());
                break;
            case "updated_date":
                esTicket.setUpdatedDate((String) entry.getValue());
                break;
            case "created_date_ts":
                longValue = ((Number) entry.getValue()).longValue();
                esTicket.setCreatedDateTS(longValue);
                break;
            case "updated_date_ts":
                longValue = ((Number) entry.getValue()).longValue();
                esTicket.setUpdatedDateTS(longValue);
                break;
            case "last_updated_by":
                longValue = ((Number) entry.getValue()).longValue();
                esTicket.setLastUpdatedBy(longValue);
                break;
            case "is_escalated":
                esTicket.setEscalated((Boolean) entry.getValue());
                break;
            case "escalated_date":
                esTicket.setEscalatedDate((String) entry.getValue());
                break;
            case "escalated_date_ts":
                longValue = ((Number) entry.getValue()).longValue();
                esTicket.setEscalatedDateTS(longValue);
                break;
            case "escalated_to":
                longValue = ((Number) entry.getValue()).longValue();
                esTicket.setEscalatedTo(longValue);
                break;
            case "status":
                for (TicketStatus enumValue : TicketStatus.values()) {
                    if (enumValue.name().equals(entry.getValue().toString())) {
                        esTicket.setStatus(enumValue);
                        break;
                    }
                }
                break;
            case "request_type":
                esTicket.setRequestType((String) entry.getValue());
                break;
            case "priority":
                for (TicketPriority enumValue : TicketPriority.values()) {
                    if (enumValue.name().equals(entry.getValue().toString())) {
                        esTicket.setPriority(enumValue);
                        break;
                    }
                }
                break;
            case "escalated_by":
                longValue = ((Number) entry.getValue()).longValue();
                esTicket.setEscalatedBy(longValue);
                break;
        }
    }

    private BoolQueryBuilder createTicketSearchQuery(SearchRequest searchRequest) {
        BoolQueryBuilder finalQuery = QueryBuilders.boolQuery();
        // search by keyword
        if (searchRequest.getSearchKeyword() != null && !searchRequest.getSearchKeyword().isBlank()) {
            RegexpQueryBuilder firstNameKeywordMatchQuery = QueryBuilders.regexpQuery("requester_first_name", ".*" + searchRequest.getSearchKeyword().toLowerCase() + ".*");
            RegexpQueryBuilder phoneKeywordMatchQuery = QueryBuilders.regexpQuery("requester_phone", ".*" + searchRequest.getSearchKeyword().toLowerCase() + ".*");
            RegexpQueryBuilder emailKeywordMatchQuery = QueryBuilders.regexpQuery("requester_email", ".*" + searchRequest.getSearchKeyword().toLowerCase() + ".*");
            BoolQueryBuilder keywordSearchQuery = QueryBuilders.boolQuery();
            keywordSearchQuery.should(firstNameKeywordMatchQuery).should(phoneKeywordMatchQuery).should(emailKeywordMatchQuery);
            finalQuery.must(keywordSearchQuery);
        }
        getPriority(String.valueOf(searchRequest.getPriority()), finalQuery);
        if(searchRequest.getFilter().get("cc") != null) {
            getCCRangeQuery(((Number) searchRequest.getFilter().get("cc")).longValue(), finalQuery);
        }
        getDateRangeQuery(searchRequest, finalQuery);
        if(searchRequest.getFilter().get("status") != null) {
            getStatusQuery((List<String>) searchRequest.getFilter().get("status"), finalQuery);
        }
        getJunkQuery(searchRequest.getIsJunk(), finalQuery);
        getEsclatedTicketsQuery(searchRequest.getIsEscalated(), finalQuery);
        return finalQuery;
    }

    private BoolQueryBuilder getPriority(String priority, BoolQueryBuilder finalQuery) {
        if (priority != null) {
            MatchQueryBuilder priorityMatchQuery = QueryBuilders.matchQuery("priority", priority);
            BoolQueryBuilder prioritySearchQuery = QueryBuilders.boolQuery();
            prioritySearchQuery.must(priorityMatchQuery);
            finalQuery.must(prioritySearchQuery);
        }
        return finalQuery;
    }

    private BoolQueryBuilder getCCRangeQuery(Long cc, BoolQueryBuilder finalQuery) {
        if (cc != null) {
            MatchQueryBuilder ccMatchQuery = QueryBuilders.matchQuery("assigned_to_id", cc);
            BoolQueryBuilder ccSearchQuery = QueryBuilders.boolQuery();
            ccSearchQuery.must(ccMatchQuery);
            finalQuery.must(ccSearchQuery);
        }
        return finalQuery;
    }

    private BoolQueryBuilder getJunkQuery(Boolean isJunk, BoolQueryBuilder finalQuery) {
        if (isJunk != null) {
            MatchQueryBuilder junkMatchQuery = QueryBuilders.matchQuery("is_junk", isJunk);
            BoolQueryBuilder junkSearchQuery = QueryBuilders.boolQuery();
            junkSearchQuery.must(junkMatchQuery);
            finalQuery.must(junkSearchQuery);
        }
        return finalQuery;
    }

    private BoolQueryBuilder getEsclatedTicketsQuery(Boolean isEscalated, BoolQueryBuilder finalQuery) {
        if (isEscalated != null) {
            MatchQueryBuilder esclatedMatchQuery = QueryBuilders.matchQuery("is_escalated", isEscalated);
            BoolQueryBuilder esclatedSearchQuery = QueryBuilders.boolQuery();
            esclatedSearchQuery.must(esclatedMatchQuery);
            finalQuery.must(esclatedSearchQuery);
        }
        return finalQuery;
    }

    private BoolQueryBuilder getStatusQuery(List<String> statusList, BoolQueryBuilder finalQuery) {
        if (statusList != null) {
            MatchQueryBuilder statusMatchQuery = null;
            BoolQueryBuilder statusSearchQuery = QueryBuilders.boolQuery();
            for (int i = 0; i < statusList.size(); i++) {
                statusMatchQuery = QueryBuilders.matchQuery("status", statusList.get(i));
                statusSearchQuery.should(statusMatchQuery);
            }
            finalQuery.must(statusSearchQuery);

        }
        return finalQuery;
    }

    private BoolQueryBuilder getDateRangeQuery(SearchRequest searchRequest, BoolQueryBuilder finalQuery) {
        if (searchRequest.getDate() != null && searchRequest.getDate().getFrom() != null && searchRequest.getDate().getFrom() > 0) {
            RangeQueryBuilder fromTimestampMatchQuery = QueryBuilders.rangeQuery("created_date_ts").gte(searchRequest.getDate().getFrom());
            if (searchRequest.getDate().getTo() != null && searchRequest.getDate().getTo() > 0) {
                fromTimestampMatchQuery.lt(searchRequest.getDate().getTo());
            }
            BoolQueryBuilder timestampSearchQuery = QueryBuilders.boolQuery();
            timestampSearchQuery.must(fromTimestampMatchQuery);
            finalQuery.must(timestampSearchQuery);
        }
        return finalQuery;
    }

    private BoolQueryBuilder getGte15DaysQuery(SearchRequest searchRequest, BoolQueryBuilder finalQuery) {
        Instant currentTimestamp = Instant.now();
        RangeQueryBuilder fromTimestampMatchQuery = QueryBuilders.rangeQuery("created_date_ts").lte(currentTimestamp.minus(PENDING_15_DAYS, ChronoUnit.DAYS).toEpochMilli());
        BoolQueryBuilder timestampSearchQuery = QueryBuilders.boolQuery();
        timestampSearchQuery.must(fromTimestampMatchQuery);
        finalQuery.must(timestampSearchQuery);
        return finalQuery;
    }

    private BoolQueryBuilder getGte21DaysQuery(SearchRequest searchRequest, BoolQueryBuilder finalQuery) {
        Instant currentTimestamp = Instant.now();
        RangeQueryBuilder fromTimestampMatchQuery = QueryBuilders.rangeQuery("created_date_ts").lte(currentTimestamp.minus(PENDING_21_DAYS, ChronoUnit.DAYS).toEpochMilli());
        BoolQueryBuilder timestampSearchQuery = QueryBuilders.boolQuery();
        timestampSearchQuery.must(fromTimestampMatchQuery);
        finalQuery.must(timestampSearchQuery);
        return finalQuery;
    }
}