/*
 * Copyright 2015-2018 Everlaw
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.event.ChangeListener;
import org.json.simple.JSONObject;
import org.netbeans.modules.csl.api.Error;
import org.netbeans.modules.csl.api.Severity;
import org.netbeans.modules.csl.spi.DefaultError;
import org.netbeans.modules.csl.spi.ParserResult;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.api.Task;
import org.netbeans.modules.parsing.spi.ParseException;
import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.modules.parsing.spi.SourceModificationEvent;
import org.openide.filesystems.FileObject;

/**
 *
 * @author jeffrey
 */
public class TSParser extends Parser {

    private Result result;

    @Override
    public void parse(Snapshot snapshot, Task task, SourceModificationEvent event) throws ParseException {
        TSService.updateFile(snapshot);
        result = new ParserResult(snapshot) {
            @Override
            public List<? extends Error> getDiagnostics() {
                return diagnostics(getSnapshot().getSource().getFileObject());
            }

            @Override
            protected void invalidate() {}
        };
    }

    @Override
    public Result getResult(Task task) throws ParseException {
        return result;
    }

    private List<DefaultError> diagnostics(FileObject fo) {
        List<String> metaErrors;
        List<JSONObject> normalErrors = Collections.emptyList();
        try {
            JSONObject diags = (JSONObject) TSService.callEx("getDiagnostics", fo);
            metaErrors = (List<String>) diags.get("metaErrors");
            normalErrors = (List<JSONObject>) diags.get("errs");
        } catch (TSService.TSException e) {
            metaErrors = Arrays.asList(e.getMessage());
        }

        List<DefaultError> errors = new ArrayList<>();
        for (String metaError: metaErrors) {
            errors.add(new DefaultError(null, metaError, null, fo, 0, 1, true, Severity.ERROR));
        }
        for (JSONObject err: normalErrors) {
            int start = ((Number) err.get("start")).intValue();
            int length = ((Number) err.get("length")).intValue();
            String messageText = (String) err.get("messageText");
            int category = ((Number) err.get("category")).intValue();
            int code = ((Number) err.get("code")).intValue();
            errors.add(new DefaultError(Integer.toString(code), messageText, null,
                    fo, start, start + length, false,
                    category == 0 ? Severity.WARNING : Severity.ERROR));
        }
        return errors;
    }

    @Override
    public void addChangeListener(ChangeListener cl) {
    }

    @Override
    public void removeChangeListener(ChangeListener cl) {
    }
}
