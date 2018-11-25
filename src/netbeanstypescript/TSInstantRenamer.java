/*
 * Copyright 2015-2016 Everlaw
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.simple.JSONObject;
import org.netbeans.modules.csl.api.InstantRenamer;
import org.netbeans.modules.csl.api.OffsetRange;
import org.netbeans.modules.csl.spi.ParserResult;
import org.openide.filesystems.FileObject;

/**
 *
 * @author jeffrey
 */
public class TSInstantRenamer implements InstantRenamer {

    @Override
    public boolean isRenameAllowed(ParserResult info, int caretOffset, String[] explanationRetValue) {
        return true;
    }

    @Override
    public Set<OffsetRange> getRenameRegions(ParserResult info, int caretOffset) {
        FileObject file = info.getSnapshot().getSource().getFileObject();
        Object arr = TSService.call("findRenameLocations", file, caretOffset, false, false);
        if (arr == null) {
            return null;
        }
        Set<OffsetRange> set = new HashSet<>();
        for (JSONObject loc: (List<JSONObject>) arr) {
            if (! loc.get("fileName").equals(file.getPath())) {
                // There's a reference in another file; can't instant rename. Return null so that
                // InstantRenameAction will start a refactoring rename instead.
                return null;
            }
            set.add(new OffsetRange(
                    ((Number) loc.get("start")).intValue(),
                    ((Number) loc.get("end")).intValue()));
        }
        return set;
    }
}
