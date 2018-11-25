/*
 * Copyright 2017-2018 Everlaw
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

import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import org.json.simple.JSONObject;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.netbeans.modules.parsing.api.ParserManager;
import org.netbeans.modules.parsing.api.ResultIterator;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.api.UserTask;
import org.netbeans.modules.parsing.spi.ParseException;
import org.openide.awt.StatusDisplayer;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;
import org.openide.util.Utilities;

/**
 *
 * @author jeffrey
 */
public class CompileAction extends AbstractAction {

    public CompileAction() {
        super("Compile File");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        final Collection<? extends FileObject> fileObjects =
                Utilities.actionsGlobalContext().lookupAll(FileObject.class);
        class CompileTask extends UserTask implements Runnable {
            @Override
            public void run() {
                ProgressHandle progress = ProgressHandleFactory.createHandle("TypeScript compile");
                progress.start();
                try {
                    List<Source> sources = new ArrayList<>(fileObjects.size());
                    for (FileObject fileObj: fileObjects) {
                        sources.add(Source.create(fileObj));
                    }
                    ParserManager.parse(sources, this);
                } catch (ParseException e) {
                    if (e.getCause() instanceof TSService.TSException) {
                        ((TSService.TSException) e.getCause()).notifyLater();
                    } else {
                        Exceptions.printStackTrace(e);
                    }
                } finally {
                    progress.finish();
                }
            }
            @Override
            public void run(ResultIterator ri) throws Exception {
                FileObject fileObj = ri.getParserResult().getSnapshot().getSource().getFileObject();
                writeEmitOutput(fileObj, TSService.callEx("getEmitOutput", fileObj));
            }
        }
        RequestProcessor.getDefault().post(new CompileTask());
    }

    public static void writeEmitOutput(FileObject src, Object res) throws TSService.TSException {
        if (res == null) {
            return;
        }
        for (JSONObject file: (List<JSONObject>) ((JSONObject) res).get("outputFiles")) {
            String name = (String) file.get("name");
            boolean writeBOM = Boolean.TRUE.equals(file.get("writeByteOrderMark"));
            String text = (String) file.get("text");
            TSService.log.log(Level.FINE, "Writing {0}", name);
            // Using the FileObject API instead of direct FS access ensures that the changes
            // show up in the IDE quickly.
            try (Writer w = new OutputStreamWriter(FileUtil.createData(new File(name)).getOutputStream(),
                    StandardCharsets.UTF_8)) {
                if (writeBOM) w.write('\uFEFF');
                w.write(text);
            } catch (IOException e) {
                throw new TSService.TSException("Could not write file " + name + "\n" + e);
            }
        }
        StatusDisplayer.getDefault().setStatusText(src.getNameExt() + " compiled.");
    }
}
