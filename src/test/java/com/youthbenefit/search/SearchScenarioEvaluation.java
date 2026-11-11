package com.youthbenefit.search;

import com.youthbenefit.ai.LlmClient;
import com.youthbenefit.ai.LlmProperties;
import com.youthbenefit.policy.Policy;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Explicit manual eval: real provider calls, never included in ordinary unit tests. */
public class SearchScenarioEvaluation {
    static final JsonMapper JSON = JsonMapper.builder().build();

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args.length > 0 ? args[0] : "build/search-evaluation.json");
        String prompt = args.length > 1 ? Files.readString(Path.of(args[1])) : QueryInterpreter.SYSTEM;
        boolean legacy = args.length > 2 && "legacy".equals(args[2]);
        JsonNode fixture;
        try (InputStream in = SearchScenarioEvaluation.class.getResourceAsStream("/fixtures/search-scenarios.json")) {
            fixture = JSON.readTree(in);
        }
        Properties env = new Properties();
        if (Files.exists(Path.of(".env"))) {
            try (var in = Files.newBufferedReader(Path.of(".env"), StandardCharsets.UTF_8)) { env.load(in); }
        }
        LlmProperties properties = new LlmProperties(value(env,"PWC_LLM_BASE_URL", ""), value(env,"PWC_LLM_API_KEY", ""),
            value(env,"PWC_LLM_MODEL", "openai.gpt-6-luna"), value(env,"PWC_LLM_REVIEW_MODEL", "openai.gpt-6.1-sol"),
            60, 500000, Double.parseDouble(value(env,"PWC_LLM_TEMPERATURE","1")), value(env,"PWC_LLM_REASONING_EFFORT","none"));
        LlmClient client = new LlmClient(properties, RestClient.builder(), JSON, Clock.systemUTC());
        List<Policy> corpus = new ArrayList<>();
        Map<Long,String> ids = new LinkedHashMap<>();
        for (JsonNode row : fixture.path("policies")) {
            var constructor = Policy.class.getDeclaredConstructor(); constructor.setAccessible(true);
            Policy p = constructor.newInstance();
            long id = corpus.size()+1L;
            set(p,"id",id); set(p,"title",row.path("title").asString()); set(p,"description",row.path("description").asString());
            set(p,"categoryGroup",row.path("category").asString()); set(p,"nationwide",true);
            ids.put(id,row.path("id").asString()); corpus.add(p);
        }
        List<java.util.concurrent.Future<Map<String,Object>>> futures = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(3)) {
            for (JsonNode scenario : fixture.path(System.getProperty("search.eval.suite", "scenarios"))) {
                futures.add(executor.submit(() -> {
                    Map<String,Object> row = new LinkedHashMap<>();
                    row.put("id",scenario.path("id").asString()); row.put("query",scenario.path("query").asString());
                    long start = System.nanoTime();
                    try {
                        JsonNode raw = client.json(client.defaultModel(),prompt,scenario.path("query").asString(),"search_query",QueryInterpreter.SCHEMA);
                        SearchQuery query = QueryInterpreter.parse(raw);
                        if (legacy && !query.hasFilters()) query = QueryInterpreter.words(scenario.path("query").asString());
                        List<String> found = SearchService.find(corpus,query,LocalDate.of(2026,11,11)).stream()
                            .sorted(Comparator.comparingInt(SearchService.Scored::needHits).thenComparingInt(SearchService.Scored::score).reversed())
                            .map(s -> ids.get(s.policy().getId())).toList();
                        List<String> failures = check(scenario.path("expected"),query,found);
                        row.put("raw",raw); row.put("understood",query); row.put("retrieved",found); row.put("failures",failures); row.put("passed",failures.isEmpty());
                    } catch (Exception ex) {
                        row.put("error",ex.getClass().getSimpleName()); row.put("passed",false);
                    }
                    row.put("seconds",(System.nanoTime()-start)/1e9);
                    System.out.println(row.get("id")+": "+row.get("passed")+" "+row.getOrDefault("failures",row.get("error")));
                    return row;
                }));
            }
            List<Map<String,Object>> rows = new ArrayList<>();
            for (var future : futures) rows.add(future.get());
            long passed=rows.stream().filter(r -> Boolean.TRUE.equals(r.get("passed"))).count();
            List<Double> times=rows.stream().map(r -> (Double)r.get("seconds")).sorted().toList();
            Map<String,Object> report=new LinkedHashMap<>();
            report.put("model",client.defaultModel()); report.put("temperature",properties.temperature()); report.put("reasoningEffort",properties.reasoningEffort());
            report.put("scenarios",rows.size()); report.put("passed",passed); report.put("medianSeconds",times.get(times.size()/2));
            report.put("tokens",client.totalTokens()); report.put("legacyEmptyFallback",legacy); report.put("results",rows);
            Files.createDirectories(output.toAbsolutePath().getParent());
            Files.writeString(output,JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report)+"\n");
            System.out.println("RESULT "+passed+"/"+rows.size()+"; "+output);
        }
    }

    static String value(Properties env,String key,String fallback) {
        return System.getenv(key)!=null?System.getenv(key):env.getProperty(key,fallback);
    }
    static void set(Policy policy,String key,Object value) throws Exception {
        var field=Policy.class.getDeclaredField(key); field.setAccessible(true); field.set(policy,value);
    }
    static List<String> check(JsonNode expected, SearchQuery query, List<String> found) {
        List<String> failures=new ArrayList<>();
        if (!query.ai()) failures.add("AI interpretation replaced with literal words");
        if(expected.path("emptyNeed").asBoolean(false) && !query.keywords().isEmpty()) failures.add("unexpected benefit keywords");
        if(expected.path("emptyWho").asBoolean(false) && !query.audience().isEmpty()) failures.add("unexpected audience");
        if(expected.has("needAny") && !contains(query.keywords(),expected.path("needAny"))) failures.add("benefit intent missing");
        if(expected.has("whoAny") && !contains(query.audience(),expected.path("whoAny"))) failures.add("audience missing");
        if(expected.has("categories")) {
            List<String> categories=new ArrayList<>(); expected.path("categories").forEach(c->categories.add(c.asString()));
            if(!query.categories().equals(categories)) failures.add("wrong categories");
        }
        if(expected.has("region") && !java.util.Objects.equals(query.region(),expected.path("region").asString(null))) failures.add("wrong region");
        if(expected.has("age") && !java.util.Objects.equals(query.age(),expected.path("age").isNull()?null:Integer.valueOf(expected.path("age").asInt()))) failures.add("wrong age");
        if(expected.has("closingSoon") && query.closingSoon()!=expected.path("closingSoon").asBoolean()) failures.add("wrong deadline filter");
        List<List<String>> all=new ArrayList<>(query.keywords()); all.addAll(query.audience());
        for(JsonNode word:expected.path("forbidden")) {
            if(all.stream().flatMap(List::stream).anyMatch(w->w.contains(word.asString()))) failures.add("forbidden keyword: "+word.asString());
        }
        for(JsonNode id:expected.path("include")) if(!found.contains(id.asString())) failures.add("missing policy: "+id.asString());
        for(JsonNode id:expected.path("exclude")) if(found.contains(id.asString())) failures.add("unrelated policy: "+id.asString());
        return failures;
    }
    static boolean contains(List<List<String>> groups,JsonNode expected) {
        for(JsonNode word:expected) if(groups.stream().flatMap(List::stream).anyMatch(w->w.contains(word.asString()))) return true;
        return false;
    }
}
