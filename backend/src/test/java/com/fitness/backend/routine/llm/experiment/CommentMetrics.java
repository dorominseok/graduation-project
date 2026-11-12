package com.fitness.backend.routine.llm.experiment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 설명 문장의 지표 — 프롬프트를 바꿨을 때 설명이 나아졌는지를 숫자로 본다.
 *
 * <p>통제(L1~L6)는 통과·실패가 분명하지만 설명의 질은 그렇지 않다. 실사용 설문에서 설명이 3.67점이었고,
 * r3의 설명을 읽어 보니 약점이 넷이었다: 고른 종목을 차례로 다시 읽는다, 길다, 날마다 같은 틀이다,
 * 어깨·팔을 "큰 근육"이라고 한다. 넷을 기계로 셀 수 있게 정의한다. 글자 맞추기라 완전하지 않으므로 —
 * "큰 근육" 오용은 걸린 문장을 그대로 같이 찍어 눈으로 확인한다.
 */
final class CommentMetrics {

    /** 설명 하나. {@code names}는 그 루틴에서 고른 종목, {@code muscles}는 그 루틴의 부위(MuscleGroup 이름) */
    record Sample(String caseId, List<String> muscles, List<String> names, String comment) {
    }

    private static final List<String> STOCK_WORDS = List.of("순서", "먼저", "마무리", "배치", "큰 근육");
    private static final Set<String> BIG = Set.of("CHEST", "BACK", "QUADS", "POSTERIOR");
    private static final String SMALL_WORD = "(어깨|삼두|이두|팔|코어)";
    /** "큰 근육인 등과 어깨 후면" — 바로 뒤에 나열한 부위에 작은 근육이 있다 */
    private static final Pattern BIG_THEN_LIST = Pattern.compile("큰 근육인 ([^,.]{1,24}?)(?:[을를은는이가]\\s|[,.]|$)");
    /** "어깨 후면을 큰 근육 위주로" — 작은 근육을 큰 근육이라고 부른다 */
    private static final Pattern SMALL_AS_BIG = Pattern.compile(SMALL_WORD + "[가-힣· ]{0,6}[을를은는]? 큰 근육");

    private CommentMetrics() {
    }

    static String render(List<Sample> samples) {
        if (samples.isEmpty()) {
            return "LLM이 쓴 설명이 없다.\n";
        }
        int n = samples.size();
        int listsAll = 0;
        int threeOrMore = 0;
        double namedShare = 0;
        int totalLength = 0;
        int maxLength = 0;
        Map<String, Integer> stock = new LinkedHashMap<>();
        Map<String, Integer> masked = new HashMap<>();
        Map<String, Integer> openings = new HashMap<>();
        List<String> misuse = new ArrayList<>();

        for (Sample s : samples) {
            int named = named(s);
            if (named == s.names().size()) {
                listsAll++;
            }
            if (named >= 3) {
                threeOrMore++;
            }
            namedShare += s.names().isEmpty() ? 0 : (double) named / s.names().size();
            totalLength += s.comment().length();
            maxLength = Math.max(maxLength, s.comment().length());
            for (String word : STOCK_WORDS) {
                if (s.comment().contains(word)) {
                    stock.merge(word, 1, Integer::sum);
                }
            }
            masked.merge(mask(s), 1, Integer::sum);
            openings.merge(opening(mask(s)), 1, Integer::sum);
            if (misusesBigMuscle(s)) {
                misuse.add(s.caseId() + " " + s.comment());
            }
        }
        int repeated = masked.values().stream().filter(count -> count >= 2).mapToInt(Integer::intValue).sum();

        StringBuilder md = new StringBuilder();
        md.append("| 지표 | 값 |\n|---|---|\n")
                .append("| LLM이 쓴 설명 | ").append(n).append("개 |\n")
                .append("| **종목 나열** — 고른 종목을 전부 다시 말한 설명 | ").append(ratio(listsAll, n)).append(" |\n")
                .append("| 종목 이름을 셋 이상 말한 설명 | ").append(ratio(threeOrMore, n)).append(" |\n")
                .append("| 고른 종목 가운데 이름을 말한 비율(평균) | ").append(Math.round(namedShare * 100 / n)).append("% |\n")
                .append("| **길이** — 평균 / 최대 | ").append(Math.round((double) totalLength / n)).append("자 / ")
                .append(maxLength).append("자 |\n")
                .append("| **반복** — 종목 이름을 가리면 다른 설명과 같은 문장이 되는 설명 | ").append(ratio(repeated, n)).append(" |\n")
                .append("| 종목 이름을 가린 문장의 가짓수 | ").append(masked.size()).append("가지 |\n")
                .append("| 문장을 여는 말(이름을 가린 첫 두 어절)의 가짓수 | ").append(openings.size()).append("가지 |\n")
                .append("| 가장 흔한 여는 말 | ").append(openings.entrySet().stream()
                        .max(Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                        .map(e -> "\"" + e.getKey() + "\" " + ratio(e.getValue(), n)).orElse("—")).append(" |\n");
        for (String word : STOCK_WORDS) {
            md.append("| \"").append(word).append("\" 포함 | ").append(ratio(stock.getOrDefault(word, 0), n)).append(" |\n");
        }
        md.append("| **\"큰 근육\" 오용** — 어깨·팔·코어를 큰 근육이라고 한 설명 | ").append(ratio(misuse.size(), n)).append(" |\n");
        if (!misuse.isEmpty()) {
            md.append("\n\"큰 근육\" 오용으로 센 문장:\n");
            misuse.forEach(line -> md.append("- ").append(line).append('\n'));
        }
        md.append("\n가장 자주 나온 틀(종목 이름을 ○로 가림):\n");
        masked.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()).thenComparing(Map.Entry.comparingByKey()))
                .limit(5)
                .forEach(e -> md.append("- ").append(e.getValue()).append("회: ").append(e.getKey()).append('\n'));
        return md.toString();
    }

    /** 고른 종목 가운데 설명에 이름이 나온 수. 줄여 쓴 이름("랫풀다운: 와이드 그립" → "랫풀다운")도 센다 */
    static int named(Sample s) {
        return (int) s.names().stream().filter(name -> variants(name).stream().anyMatch(s.comment()::contains)).count();
    }

    /** 종목 이름(줄인 꼴 포함)을 ○로 가린 문장. 긴 이름부터 가려야 "바벨 벤치프레스"가 "바벨 ○"로 남지 않는다 */
    static String mask(Sample s) {
        String text = s.comment();
        List<String> all = s.names().stream().flatMap(name -> variants(name).stream())
                .sorted(Comparator.comparingInt(String::length).reversed()).toList();
        for (String variant : all) {
            text = text.replace(variant, "○");
        }
        return text;
    }

    private static String opening(String maskedComment) {
        String[] words = maskedComment.strip().split("\\s+");
        return words.length <= 2 ? maskedComment.strip() : words[0] + " " + words[1];
    }

    /** 온 이름, 콜론 앞, 거기서 첫 낱말(기구)을 뺀 것, 네 글자 이상인 마지막 낱말 */
    static Set<String> variants(String name) {
        Set<String> result = new LinkedHashSet<>();
        result.add(name);
        String base = name.split(":")[0].strip();
        result.add(base);
        String[] words = base.split("\\s+");
        if (words.length >= 2) {
            result.add(String.join(" ", List.of(words).subList(1, words.length)));
            String last = words[words.length - 1];
            if (last.length() >= 4) {
                result.add(last);
            }
        }
        return result;
    }

    static boolean misusesBigMuscle(Sample s) {
        String comment = s.comment();
        if (!comment.contains("큰 근육")) {
            return false;
        }
        Matcher list = BIG_THEN_LIST.matcher(comment);
        while (list.find()) {
            if (Pattern.compile(SMALL_WORD).matcher(list.group(1)).find()) {
                return true;
            }
        }
        if (SMALL_AS_BIG.matcher(comment).find()) {
            return true;
        }
        // 큰 근육이 하나도 없는 루틴에서 큰 근육을 말한다
        return s.muscles().stream().noneMatch(BIG::contains);
    }

    private static String ratio(int count, int total) {
        return count + "/" + total + " (" + Math.round(count * 100.0 / total) + "%)";
    }

    /** raw.jsonl의 items("CHEST 바벨 벤치프레스 ×3")에서 부위와 종목 이름을 꺼낸다 */
    static Sample fromRecord(String caseId, List<String> items, String comment) {
        List<String> muscles = items.stream().map(i -> i.substring(0, i.indexOf(' '))).distinct().collect(Collectors.toList());
        List<String> names = items.stream()
                .map(i -> i.substring(i.indexOf(' ') + 1).replaceAll(" ×\\d+$", ""))
                .collect(Collectors.toList());
        return new Sample(caseId, muscles, names, comment);
    }
}
