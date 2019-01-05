/*
 * Copyright 2015 Everlaw
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package netbeanstypescript;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.simple.JSONObject;
import org.netbeans.modules.csl.api.ColoringAttributes;
import org.netbeans.modules.csl.api.OffsetRange;
import org.netbeans.modules.csl.api.SemanticAnalyzer;
import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.modules.parsing.spi.Scheduler;
import org.netbeans.modules.parsing.spi.SchedulerEvent;

/**
 *
 * @author jeffrey
 */
public class TSSemanticAnalyzer extends SemanticAnalyzer<Parser.Result> {

    private Map<OffsetRange, Set<ColoringAttributes>> result;

    @Override
    public Map<OffsetRange, Set<ColoringAttributes>> getHighlights() {
        return result;
    }

    @Override
    public void run(Parser.Result t, SchedulerEvent se) {
        Object highlights = TSService.call("getSemanticHighlights",
                t.getSnapshot().getSource().getFileObject());
        if (highlights == null) {
            result = Collections.emptyMap();
            return;
        }
        Map<OffsetRange, Set<ColoringAttributes>> map = new HashMap<>();
        for (JSONObject hi: (List<JSONObject>) highlights) {
            int start = ((Number) hi.get("s")).intValue();
            int length = ((Number) hi.get("l")).intValue();
            EnumSet<ColoringAttributes> atts = EnumSet.noneOf(ColoringAttributes.class);
            for (String attr: (List<String>) hi.get("a")) {
                atts.add(ColoringAttributes.valueOf(attr));
            }
            map.put(new OffsetRange(start, start + length), atts);
        }
        result = map;
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public Class<? extends Scheduler> getSchedulerClass() {
        return Scheduler.EDITOR_SENSITIVE_TASK_SCHEDULER;
    }

    @Override
    public void cancel() {}
}
