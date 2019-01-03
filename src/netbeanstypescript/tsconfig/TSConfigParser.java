/*
 * Copyright 2016-2018 Everlaw
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
package netbeanstypescript.tsconfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.event.ChangeListener;
import netbeanstypescript.TSService;
import netbeanstypescript.lexer.api.JsTokenId;
import netbeanstypescript.tsconfig.TSConfigCodeCompletion.TSConfigElementHandle;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.netbeans.api.lexer.TokenSequence;
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

 * @author jeffrey
 */
public class TSConfigParser extends Parser {

    static class ConfigNode {
        int keyOffset;
        int startOffset;
        int endOffset;
        boolean missing;
        Object value;
        List<ConfigNode> elements;
        HashMap<String, ConfigNode> properties;

        Map<String, TSConfigElementHandle> validMap;
        Object expectedType;
    }

    static class Result extends ParserResult {
        final FileObject fileObj;
        List<Error> errors = new ArrayList<>();
        ConfigNode root;

        Result(Snapshot snapshot) {
            super(snapshot);
            fileObj = snapshot.getSource().getFileObject();
        }

        void addError(String error, ConfigNode node) {
            addError(error, node.startOffset, node.endOffset, Severity.ERROR);
        }

        void addError(String error, int start, int end) {
            addError(error, start, end, Severity.ERROR);
        }

        void addError(String error, int start, int end, Severity sev) {
            errors.add(new DefaultError(null, error, null, fileObj, start, end, false, sev));
        }

        @Override
        public List<? extends Error> getDiagnostics() { return errors; }
        @Override
        protected void invalidate() {}
    }

    Result result;

    @Override
    public void parse(Snapshot snapshot, Task task, SourceModificationEvent sme) throws ParseException {
        final TokenSequence<JsTokenId> ts =
                snapshot.getTokenHierarchy().tokenSequence(JsTokenId.javascriptLanguage());
        final Result res = new Result(snapshot);

        res.root = (new Object() {
            private JsTokenId advance() {
                while (ts.moveNext()) {
                    JsTokenId id = ts.token().id();
                    String category = id.primaryCategory();
                    if (! ("comment".equals(category) || "whitespace".equals(category))) {
                        return id;
                    }
                }
                return null;
            }
            private void error(String msg) {
                res.addError(msg, ts.offset(), ts.offset() + ts.token().length());
            }
            private boolean nextListItem(boolean previous, JsTokenId endToken, String type) {
                if (! previous) {
                    JsTokenId id = advance();
                    if (id == null || id == JsTokenId.BRACKET_RIGHT_BRACKET || id == JsTokenId.BRACKET_RIGHT_CURLY) {
                        if (id != endToken) {
                            if (id != null) ts.movePrevious();
                            error("Unterminated " + type + ".");
                        }
                        return false;
                    }
                    return true;
                } else {
                    JsTokenId id = advance();
                    if (id == JsTokenId.OPERATOR_COMMA) {
                        id = advance();
                        if (id == null) {
                            error("Unterminated " + type + ".");
                            return false;
                        }
                        return true;
                    } else if (id == null || id == JsTokenId.BRACKET_RIGHT_BRACKET || id == JsTokenId.BRACKET_RIGHT_CURLY) {
                        if (id != endToken) {
                            if (id != null) ts.movePrevious();
                            error("Unterminated " + type + ".");
                        }
                        return false;
                    } else {
                        error("Missing comma.");
                        return true;
                    }
                }
            }
            private ConfigNode value() {
                ConfigNode node = new ConfigNode();
                node.startOffset = ts.offset();
                switch (ts.token().id()) {
                    case KEYWORD_NULL:  break;
                    case KEYWORD_TRUE:  node.value = Boolean.TRUE; break;
                    case KEYWORD_FALSE: node.value = Boolean.FALSE; break;
                    case STRING: case NUMBER:
                        // TODO: negative numbers
                        try {
                            node.value = JSONValue.parseWithException(ts.token().text().toString());
                        } catch (org.json.simple.parser.ParseException e) {
                            error("Invalid JSON literal: " + e);
                            return null;
                        }
                        break;
                    case BRACKET_LEFT_BRACKET:
                        node.elements = new ArrayList<>();
                        for (boolean in = false;
                                (in = nextListItem(in, JsTokenId.BRACKET_RIGHT_BRACKET, "array")); ) {
                            ConfigNode element = value();
                            if (element != null) {
                                node.elements.add(element);
                            }
                        }
                        break;
                    case BRACKET_LEFT_CURLY:
                        node.properties = new LinkedHashMap<>();
                        for (boolean in = false;
                                (in = nextListItem(in, JsTokenId.BRACKET_RIGHT_CURLY, "object")); ) {
                            ConfigNode keyNode = value();
                            if (keyNode == null) continue;
                            String key = null;
                            if (keyNode.value instanceof String) {
                                key = (String) keyNode.value;
                            } else {
                                res.addError("JSON object key must be a string.", keyNode);
                            }
                            int colonOffset = keyNode.endOffset;
                            ConfigNode valueNode;
                            if (advance() == JsTokenId.OPERATOR_COLON) {
                                colonOffset = ts.offset() + 1;
                                advance();
                            } else {
                                res.addError("JSON object must consist of '\"key\": value' pairs.", keyNode);
                            }
                            valueNode = value();
                            if (valueNode == null) {
                                valueNode = new ConfigNode();
                                valueNode.missing = true;
                                valueNode.startOffset = colonOffset;
                                valueNode.endOffset = ts.offset() + ts.token().length();
                            }
                            valueNode.keyOffset = keyNode.startOffset;
                            if (key != null) {
                                ConfigNode oldValue = node.properties.put(key, valueNode);
                                if (oldValue != null) {
                                    res.addError("Duplicate key '" + key + "' will be ignored.",
                                            oldValue.keyOffset, oldValue.endOffset, Severity.WARNING);
                                }
                            }
                        }
                        break;
                    case OPERATOR_COMMA:
                    case BRACKET_RIGHT_BRACKET:
                    case BRACKET_RIGHT_CURLY:
                        error("Missing value.");
                        ts.movePrevious();
                        return null;
                    default:
                        error("Unexpected token type " + ts.token().id());
                        return null;
                }
                node.endOffset = ts.offset() + ts.token().length();
                return node;
            }
            ConfigNode root() {
                if (advance() == null) {
                    return null;
                }
                ConfigNode root = value();
                if (advance() != null) {
                    error("Extra text at end of JSON.");
                }
                return root;
            }
        }).root();

        ROOT: if (res.root != null) {
            if (res.root.properties == null) {
                res.addError("tsconfig.json value should be an object", res.root);
                break ROOT;
            }

            Map<String, TSConfigElementHandle> rootCompletions = res.root.validMap = new HashMap<>();

            rootCompletions.put("compileOnSave", new TSConfigElementHandle(
                    "compileOnSave", "boolean", "If true, any TypeScript files modified during the IDE session are automatically transpiled to JS."));
            ConfigNode compileOnSave = res.root.properties.get("compileOnSave");
            if (compileOnSave != null && ! (compileOnSave.value instanceof Boolean)) {
                res.addError("'compileOnSave' value must be a boolean.", compileOnSave);
            }

            rootCompletions.put("extends", new TSConfigElementHandle(
                    "extends", "string", "Path to another config file to inherit options from."));
            ConfigNode extendsOption = res.root.properties.get("extends");
            if (extendsOption != null && ! (extendsOption.value instanceof String)) {
                res.addError("'extends' value must be a string.", extendsOption);
            }

            ConfigNode files = res.root.properties.get("files");
            rootCompletions.put("files", new TSConfigElementHandle(
                    "files", "list", "Array of files to include in the project."));
            if (files != null) {
                if (files.elements == null) {
                    res.addError("'files' value must be an array of strings.", files);
                } else {
                    for (ConfigNode file: files.elements) {
                        if (! (file.value instanceof String)) {
                            res.addError("'files' element should be a string.", file);
                        }
                    }
                }
            }

            ConfigNode include = res.root.properties.get("include");
            rootCompletions.put("include", new TSConfigElementHandle(
                    "include", "list", "Array of files and directories to include in the project."));
            if (include != null) {
                if (include.elements == null) {
                    res.addError("'include' value must be an array of strings.", files);
                } else {
                    for (ConfigNode file: include.elements) {
                        if (! (file.value instanceof String)) {
                            res.addError("'include' element should be a string.", file);
                        }
                    }
                }
            }

            ConfigNode exclude = res.root.properties.get("exclude");
            rootCompletions.put("exclude", new TSConfigElementHandle(
                    "exclude", "list", "Array of files and directories not to include in the project."));
            if (exclude != null) {
                if (exclude.elements == null) {
                    res.addError("'exclude' value must be an array of strings.", exclude);
                } else {
                    for (ConfigNode file: exclude.elements) {
                        if (! (file.value instanceof String)) {
                            res.addError("'exclude' element should be a string.", file);
                        }
                    }
                }
            }

            rootCompletions.put("compilerOptions", new TSConfigElementHandle(
                    "compilerOptions", "object", null));
            ConfigNode compilerOptions = res.root.properties.get("compilerOptions");
            OPTIONS: if (compilerOptions != null) {
                if (compilerOptions.properties == null) {
                    res.addError("'compilerOptions' value should be an object.", compilerOptions);
                    break OPTIONS;
                }
                JSONArray validArray;
                try {
                    validArray = (JSONArray) TSService.callEx("getCompilerOptions", res.fileObj);
                } catch (TSService.TSException e) {
                    res.addError(e.getMessage(), compilerOptions);
                    break OPTIONS;
                }
                HashMap<String, TSConfigElementHandle> validMap = new HashMap<>();
                for (Object obj: validArray) {
                    TSConfigElementHandle eh = new TSConfigElementHandle((JSONObject) obj);
                    validMap.put(eh.getName(), eh);
                }
                compilerOptions.validMap = validMap;
                for (Map.Entry<String, ConfigNode> entry: compilerOptions.properties.entrySet()) {
                    String key = entry.getKey();
                    ConfigNode value = entry.getValue();
                    TSConfigElementHandle optionInfo = validMap.get(key);
                    if (optionInfo == null) {
                        res.addError("Unknown compiler option '" + key + "'.",
                                value.keyOffset, value.endOffset);
                        continue;
                    }
                    checkType(res, value, optionInfo);
                    if (optionInfo.deprecated != null) {
                        res.addError("'" + key + "' option is deprecated." + optionInfo.deprecated,
                                value.keyOffset, value.endOffset, Severity.WARNING);
                    }
                    if (optionInfo.commandLineOnly) {
                        res.addError("Option '" + key + "' is only meaningful when used from the command line.",
                                value.keyOffset, value.endOffset, Severity.WARNING);
                    }
                }
            }
        }

        this.result = res;
    }

    private void checkType(Result res, ConfigNode value, TSConfigElementHandle optionInfo) {
        String key = optionInfo.name;
        Object type = optionInfo.type;
        value.expectedType = type;
        if (value.missing) return;
        if (type instanceof String) {
            boolean valid = false;
            switch ((String) type) {
                case "boolean": valid = value.value instanceof Boolean; break;
                case "number": valid = value.value instanceof Number; break;
                case "string": valid = value.value instanceof String; break;
                case "list":
                    if (value.elements != null) {
                        for (ConfigNode element: value.elements) {
                            checkType(res, element, optionInfo.element);
                        }
                        valid = true;
                    }
                    break;
                case "object": valid = value.properties != null; break;
            }
            if (! valid) {
                res.addError("Compiler option '" + key + "' requires a value of type " + type + ".", value);
            }
        } else if (type instanceof JSONArray) {
            List<?> allAllowed = (List<?>) type;
            Object v = value.value;
            if (! (v instanceof String && allAllowed.contains(((String) v).toLowerCase()))) {
                StringBuilder sb = new StringBuilder("Compiler option '").append(key).append("' must be one of: ");
                boolean first = true;
                for (Object allowed: allAllowed) {
                    sb.append(first ? "'" : ", '").append(allowed).append('\'');
                    first = false;
                }
                res.addError(sb.append('.').toString(), value);
            }
        }
    }

    @Override
    public Parser.Result getResult(Task task) throws ParseException {
        return result;
    }
    @Override
    public void addChangeListener(ChangeListener cl) {}
    @Override
    public void removeChangeListener(ChangeListener cl) {}
}
