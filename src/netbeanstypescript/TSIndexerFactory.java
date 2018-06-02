/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2015 Everlaw. All rights reserved.
 *
 * Oracle and Java are registered trademarks of Oracle and/or its affiliates.
 * Other names may be trademarks of their respective owners.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 */
package netbeanstypescript;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.prefs.Preferences;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.spi.indexing.Context;
import org.netbeans.modules.parsing.spi.indexing.CustomIndexer;
import org.netbeans.modules.parsing.spi.indexing.CustomIndexerFactory;
import org.netbeans.modules.parsing.spi.indexing.Indexable;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Pair;

/**
 * This "indexer" doesn't really index anything, it's just a way to read all the TS files in a
 * project and get notified when they're changed or deleted.
 * @author jeffrey
 */
public class TSIndexerFactory extends CustomIndexerFactory {

    private final Set<String> openRoots = Collections.synchronizedSet(new HashSet<String>());

    @Override
    public boolean scanStarted(Context context) {
        if (! context.checkForEditorModifications()) {
            TSService.preIndex(context.getRootURI());
        }
        return true;
    }

    @Override
    public CustomIndexer createIndexer() {
        return new CustomIndexer() {

            @Override
            protected void index(Iterable<? extends Indexable> files, Context context) {
                FileObject root = context.getRoot();
                if (root == null) {
                    return;
                }
                boolean isOpening = openRoots.add(context.getRootURI().toString());
                Set<String> extraPaths = new HashSet<>();
                NB9_WORKAROUND: if (isOpening) {
                    FileObject timestamps = context.getIndexFolder().getParent().getParent()
                            .getFileObject("timestamps.properties");
                    if (timestamps == null) {
                        break NB9_WORKAROUND;
                    }
                    try (InputStream is = timestamps.getInputStream()) {
                        BufferedReader br = new BufferedReader(new InputStreamReader(is, UTF_8));
                        for (String line; (line = br.readLine()) != null; ) {
                            int valuePos = line.lastIndexOf('=');
                            if (valuePos >= 0) extraPaths.add(line.substring(0, valuePos));
                        }
                    } catch (IOException e) {
                        TSService.log.log(Level.WARNING, "Could not read {0}: {1}",
                                new Object[] { timestamps, e });
                    }
                }
                List<Pair<Indexable, Snapshot>> snapshots = new ArrayList<>();
                for (Indexable indxbl: files) {
                    extraPaths.remove(indxbl.getRelativePath());
                    FileObject fo = root.getFileObject(indxbl.getRelativePath());
                    if (relevant(fo)) {
                        System.out.println("Indexing " + indxbl.getRelativePath());
                        snapshots.add(Pair.of(indxbl, Source.create(fo).createSnapshot()));
                    }
                }
                if (! snapshots.isEmpty()) {
                    TSService.addFiles(snapshots, context, isOpening || context.isSupplementaryFilesIndexing());
                }
                List<URL> extraFileObjects = new ArrayList<>();
                for (String extraPath: extraPaths) {
                    FileObject fo = root.getFileObject(extraPath);
                    if (relevant(fo)) {
                        System.out.println("Forcing index of previously indexed file: " + extraPath);
                        extraFileObjects.add(fo.toURL());
                    }
                }
                if (! extraFileObjects.isEmpty()) {
                    context.addSupplementaryFiles(context.getRootURI(), extraFileObjects);
                }
            }

            private boolean relevant(FileObject fo) {
                if (fo == null) return false;
                String mime = FileUtil.getMIMEType(fo);
                return "text/typescript".equals(mime) || "text/tsconfig+x-json".equals(mime);
            }
        };
    }

    @Override
    public void scanFinished(Context context) {
        if (! context.checkForEditorModifications()) {
            TSService.postIndex(context.getRootURI());
        }
    }

    @Override
    public boolean supportsEmbeddedIndexers() {
        return false;
    }

    @Override
    public void filesDeleted(Iterable<? extends Indexable> deleted, Context context) {
        TSService.removeFiles(deleted, context);
    }

    @Override
    public void filesDirty(Iterable<? extends Indexable> dirty, Context context) {
    }

    @Override
    public void rootsRemoved(Iterable<? extends URL> removedRoots) {
        for (URL url: removedRoots) {
            TSService.removeProgram(url);
            openRoots.remove(url.toString());
        }
    }

    @Override
    public String getIndexerName() {
        return "typescript";
    }

    @Override
    public int getIndexVersion() {
        return 0;
    }

    static void compileIfEnabled(FileObject root, FileObject[] fileObjects) {
        boolean guiSetting = false;
        Project project = FileOwnerQuery.getOwner(root);
        if (project != null) {
            Preferences prefs = ProjectUtils.getPreferences(project, TSProjectCustomizer.class, true);
            guiSetting = "true".equals(prefs.get("compileOnSave", null));
        }
        ProgressHandle progress = ProgressHandleFactory.createHandle("TypeScript compile on save");
        progress.start();
        try {
            for (FileObject fileObject: fileObjects) {
                TSService.log.log(Level.FINE, "Compiling {0}", fileObject.getPath());
                CompileAction.writeEmitOutput(fileObject,
                        TSService.call("getCompileOnSaveEmitOutput", fileObject, guiSetting));
            }
        } finally {
            progress.finish();
        }
    }
}
