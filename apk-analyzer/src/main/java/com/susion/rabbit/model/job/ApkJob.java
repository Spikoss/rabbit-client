/*
 * Tencent is pleased to support the open source community by making wechat-matrix available.
 * Copyright (C) 2018 THL A29 Limited, a Tencent company. All rights reserved.
 * Licensed under the BSD 3-Clause License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.susion.rabbit.model.job;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.susion.rabbit.ApkAnalyzer;
import com.susion.rabbit.model.result.JobResult;
import com.susion.rabbit.model.result.JobResultFactory;
import com.susion.rabbit.model.result.TaskResult;
import com.susion.rabbit.model.result.TaskResultFactory;
import com.susion.rabbit.model.result.TaskResultRegistry;
import com.susion.rabbit.model.task.ApkTask;
import com.susion.rabbit.model.task.TaskFactory;
import com.susion.rabbit.model.task.util.ApkConstants;
import com.susion.rabbit.model.utils.FileUtil;
import com.susion.rabbit.model.utils.Log;
import com.susion.rabbit.model.utils.Util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Created by jinqiuchen on 17/6/5.
 */

public final class ApkJob {

    private static final String TAG = "Matrix.ApkJob";

    private String[] args;
    private JobConfig jobConfig;

    private ExecutorService executor;
    private static final int TIMEOUT_SECONDS = 600;
    private int timeoutSeconds = TIMEOUT_SECONDS;
    private static final int THREAD_NUM = 1;
    private int threadNum = THREAD_NUM;

    private List<ApkTask> preTasks;
    private List<ApkTask> taskList;
    private List<JobResult> jobResults;

    public ApkJob(String[] args) {
        this(args, 0, 0);
    }

    public ApkJob(String[] args, int timeoutSeconds, int threadNum) {
        this.args = args;
        jobConfig = new JobConfig();
        if (timeoutSeconds > 0) {
            this.timeoutSeconds = timeoutSeconds;
        }
        if (threadNum > 0) {
            this.threadNum = threadNum;
        }
        executor = Executors.newFixedThreadPool(this.threadNum);
        this.preTasks = new ArrayList<>();
        this.taskList = new ArrayList<>();
        this.jobResults = new ArrayList<>();
    }

    private int parseParams(int start, String[] params, Map<String, String> result) {
        int end = params.length;
        String key = "";
        for (int i = start; i < params.length; i++) {
            if (params[i].startsWith("-")) {
                if (!params[i].startsWith("--")) {
                    end = i;
                    break;
                } else {
                    key = params[i];
                }
            } else {
                result.put(key, params[i]);
            }
        }
        return end - start;
    }

    private String getApkRawName(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        int index = name.indexOf('.');
        if (index == -1) {
            return name;
        }
        return name.substring(0, index);
    }

    private ApkTask createTask(String name, Map<String, String> params) {
        ApkTask task = null;
        if (JobConstants.OPTION_MANIFEST.equals(name)) {
            task = TaskFactory.factory(TaskFactory.TASK_TYPE_MANIFEST, jobConfig, params);
        } else if (JobConstants.OPTION_FILE_SIZE.equals(name)) {
            task = TaskFactory.factory(TaskFactory.TASK_TYPE_SHOW_FILE_SIZE, jobConfig, params);
        } else if (JobConstants.OPTION_COUNT_METHOD.equals(name)) {
            task = TaskFactory.factory(TaskFactory.TASK_TYPE_COUNT_METHOD, jobConfig, params);
        } else if (JobConstants.OPTION_CHECK_RES_PROGUARD.equals(name)) {
            task = TaskFactory.factory(TaskFactory.TASK_TYPE_CHECK_RESGUARD, jobConfig, params);
        } else if (JobConstants.OPTION_FIND_NON_ALPHA_PNG.equals(name)) {
            task = TaskFactory.factory(TaskFactory.TASK_TYPE_FIND_NON_ALPHA_PNG, jobConfig, params);
        } else if (JobConstants.OPTION_UNCOMPRESSED_FILE.equals(name)) {
            task = TaskFactory.factory(TaskFactory.TASK_TYPE_UNCOMPRESSED_FILE, jobConfig, params);
        } else if (JobConstants.OPTION_DUPLICATE_RESOURCES.equals(name)) {
            task = TaskFactory.factory(TaskFactory.TASK_TYPE_DUPLICATE_FILE, jobConfig, params);
        }
        return task;
    }

    private void readConfigFile(String configPath) throws IOException, ClassNotFoundException, IllegalAccessException, InstantiationException {
        StringBuilder jsonStr = new StringBuilder();
        File configFile = new File(configPath);
        BufferedReader bufferedReader = new BufferedReader(new FileReader(configFile));
        String line = bufferedReader.readLine();
        while (line != null) {
            if (!Util.isNullOrNil(line.trim())) {
                jsonStr.append(line.trim());
            }
            line = bufferedReader.readLine();
        }
        JsonParser parser = new JsonParser();
        JsonElement jsonElement = parser.parse(jsonStr.toString());
        if (jsonElement.isJsonObject()) {
            JsonObject config = (JsonObject) jsonElement;
            String value = "";
            if (config.has(JobConstants.PARAM_INPUT)) {
                value = config.get(JobConstants.PARAM_INPUT).getAsString();
                if (!Util.isNullOrNil(value)) {
                    jobConfig.setInputDir(value);
                }
            }

            value = "";
            if (config.has(JobConstants.PARAM_APK)) {
                value = config.get(JobConstants.PARAM_APK).getAsString();
            } else {
                if (!(Util.isNullOrNil(jobConfig.getInputDir()))) {
                    File inputDir = new File(jobConfig.getInputDir());
                    if (inputDir.isDirectory() && inputDir.exists()) {
                        for (File file : inputDir.listFiles()) {
                            if (file.isFile() && file.getName().endsWith(ApkConstants.APK_FILE_SUFFIX)) {
                                value = file.getAbsolutePath();
                                break;
                            }
                        }
                    }
                }
            }
            if (!FileUtil.isLegalFile(value)) {
                ApkAnalyzer.errorExit("Input apk path '" + value + "' is illegal!");
            } else {
                jobConfig.setApkPath(value);
            }
            File apkFile = new File(jobConfig.getApkPath());
            if (config.has(JobConstants.PARAM_UNZIP) && !Util.isNullOrNil(config.get(JobConstants.PARAM_UNZIP).getAsString())) {
                value = config.get(JobConstants.PARAM_UNZIP).getAsString();
            } else {
                value = apkFile.getParentFile().getAbsolutePath() + File.separator + getApkRawName(apkFile.getName()) + "_unzip";
            }
            jobConfig.setUnzipPath(value);

            if (config.has(JobConstants.PARAM_FORMAT) && !Util.isNullOrNil(config.get(JobConstants.PARAM_FORMAT).getAsString())) {
                value = config.get(JobConstants.PARAM_FORMAT).getAsString();
            } else {
                value = TaskResultFactory.TASK_RESULT_TYPE_HTML;
            }
            String[] formats = value.split(",");
            List<String> formatList = new ArrayList<>();
            for (String format : formats) {
                if (!Util.isNullOrNil(format)) {
                    formatList.add(format.trim());
                }
            }
            Log.i(TAG, "format list " + formatList.toString());
            jobConfig.setOutputFormatList(formatList);

            if (config.has(JobConstants.PARAM_OUTPUT) && !Util.isNullOrNil(config.get(JobConstants.PARAM_OUTPUT).getAsString())) {
                value = config.get(JobConstants.PARAM_OUTPUT).getAsString();
            } else {
                value = apkFile.getParentFile().getAbsolutePath() + File.separator + getApkRawName(apkFile.getName());
            }
            jobConfig.setOutputPath(value);

            if (config.has(JobConstants.PARAM_FORMAT_JAR) && !Util.isNullOrNil(config.get(JobConstants.PARAM_FORMAT_JAR).getAsString())) {
                value = config.get(JobConstants.PARAM_FORMAT_JAR).getAsString();
                File file = new File(value);
                JarFile jarFile = new JarFile(file);
                Manifest manifest = jarFile.getManifest();
                Attributes registry = manifest.getAttributes(JobConstants.TASK_RESULT_REGISTRY);
                if (registry != null) {
                    String registryClassPath = registry.getValue(JobConstants.TASK_RESULT_REGISTERY_CLASS);
                    ClassLoader classLoader = new URLClassLoader(new URL[]{file.toURL()});
                    Class registryClass = classLoader.loadClass(registryClassPath);
                    TaskResultRegistry resultRegistry = (TaskResultRegistry) registryClass.newInstance();
                    TaskResultFactory.addCustomTaskJsonResult(resultRegistry.getJsonResult());
                    TaskResultFactory.addCustomTaskHtmlResult(resultRegistry.getHtmlResult());
                }
            }

            if (config.has(JobConstants.PARAM_FORMAT_CONFIG)) {
                JsonArray outputConfig = config.get(JobConstants.PARAM_FORMAT_CONFIG).getAsJsonArray();
                if (outputConfig != null) {
                    jobConfig.setOutputConfig(outputConfig);
                }
            }

            if (config.has(JobConstants.PARAM_LOG_LEVEL)) {
                Log.setLogLevel(config.get(JobConstants.PARAM_LOG_LEVEL).getAsString());
            }

            if (config.has(JobConstants.PARAM_MAPPING_TXT) && !Util.isNullOrNil(config.get(JobConstants.PARAM_MAPPING_TXT).getAsString())) {
                jobConfig.setMappingFilePath(config.get(JobConstants.PARAM_MAPPING_TXT).getAsString());
            }

            if (config.has(JobConstants.PARAM_RES_MAPPING_TXT) && !Util.isNullOrNil(config.get(JobConstants.PARAM_RES_MAPPING_TXT).getAsString())) {
                jobConfig.setResMappingFilePath(config.get(JobConstants.PARAM_RES_MAPPING_TXT).getAsString());
            }

            JsonArray options = config.getAsJsonArray("options");
            for (JsonElement option : options) {
                if (option.isJsonObject()) {
                    JsonObject jsonOption = (JsonObject) option;
                    String name = jsonOption.get("name").getAsString();
                    Map<String, String> params = new HashMap<>();
                    for (Map.Entry<String, JsonElement> param : jsonOption.entrySet()) {
                        if (param.getKey().startsWith("--")) {
                            if (param.getValue().isJsonPrimitive()) {
                                params.put(param.getKey(), param.getValue().getAsString());
                            } else {
                                if (param.getKey().equals(JobConstants.PARAM_IGNORE_RESOURCES_LIST) || param.getKey().equals(JobConstants.PARAM_IGNORE_ASSETS_LIST)) {
                                    JsonArray ignoreList = param.getValue().getAsJsonArray();
                                    StringBuilder ignoreStrBuilder = new StringBuilder();
                                    for (JsonElement ignore : ignoreList) {
                                        ignoreStrBuilder.append(ignore.getAsString());
                                        ignoreStrBuilder.append(',');
                                    }
                                    ignoreStrBuilder.deleteCharAt(ignoreStrBuilder.length() - 1);
                                    params.put(param.getKey(), ignoreStrBuilder.toString());
                                }
                            }
                        }
                    }
                    if (name.equals(JobConstants.OPTION_UNUSED_RESOURCES) && !params.containsKey(JobConstants.PARAM_R_TXT)) {
                        String inputDir = jobConfig.getInputDir();
                        if (!Util.isNullOrNil(inputDir)) {
                            String rTxtFilePath = inputDir + "/" + ApkConstants.DEFAULT_RTXT_FILENAME;
                            params.put(JobConstants.PARAM_R_TXT, rTxtFilePath);
                        }

                    }
                    ApkTask task = createTask(name, params);
                    if (task != null) {
                        taskList.add(task);
                    }
                } else {
                    ApkAnalyzer.errorExit("Unknown option: " + option.toString());
                }
            }

        } else {
            ApkAnalyzer.errorExit("The content of config file is not in json format!");
        }
    }

    private int parseGlobalParams() throws Exception {
        int paramLen = 0;
        Map<String, String> globalParams = new HashMap<>();
        paramLen = parseParams(0, args, globalParams);

        final String configPath = globalParams.get(JobConstants.PARAM_CONFIG);
        if (!FileUtil.isLegalFile(configPath)) {
            ApkAnalyzer.errorExit("Input config file '" + configPath + "' is illegal!");
        } else if (!configPath.endsWith(".json")) {
            ApkAnalyzer.errorExit("Input config file must has a suffix '.json'!");
        } else {
            readConfigFile(configPath);
        }

        return paramLen;
    }

    private boolean parseParams() {
        if (args != null && args.length >= 2) {

            int paramLen = 0;

            for (int i = paramLen; i < args.length; i++) {
                if (args[i].startsWith("-") && !args[i].startsWith("--")) {
                    Map<String, String> params = new HashMap<>();
                    paramLen = parseParams(i + 1, args, params);
                    if (!params.containsKey(JobConstants.PARAM_R_TXT)) {
                        String inputDir = jobConfig.getInputDir();
                        if (!Util.isNullOrNil(inputDir)) {
                            params.put(JobConstants.PARAM_R_TXT, inputDir + "/" + ApkConstants.DEFAULT_RTXT_FILENAME);
                        }
                    }
                    ApkTask task = createTask(args[i], params);
                    if (task != null) {
                        taskList.add(task);
                    }
                    i += paramLen;
                }
            }
        } else {
            return false;
        }
        return true;
    }

    public void run() throws Exception {
        if (parseParams()) {
            ApkTask unzipTask = TaskFactory.factory(TaskFactory.TASK_TYPE_UNZIP, jobConfig, new HashMap<String, String>());
            preTasks.add(unzipTask);
            for (String format : jobConfig.getOutputFormatList()) {
                JobResult result = JobResultFactory.factory(format, jobConfig);
                if (result != null) {
                    jobResults.add(result);
                } else {
                    Log.w(TAG, "Unknown output format name '%s' !", format);
                }
            }
            execute();
        } else {

        }
    }

    private void execute() throws Exception {
        try {

            for (ApkTask preTask : preTasks) {
                preTask.init();
                TaskResult taskResult = preTask.call();
                if (taskResult != null) {
                    TaskResult formatResult = null;
                    for (JobResult jobResult : jobResults) {
                        formatResult = TaskResultFactory.transferTaskResult(taskResult.taskType, taskResult, jobResult.getFormat(), jobConfig);
                        if (formatResult != null) {
                            jobResult.addTaskResult(formatResult);
                        }
                    }
                }
            }
            for (ApkTask task : taskList) {
                task.init();
            }
            List<Future<TaskResult>> futures = executor.invokeAll(taskList, timeoutSeconds, TimeUnit.SECONDS);
            for (Future<TaskResult> future : futures) {
                TaskResult taskResult = future.get();
                if (taskResult != null) {
                    TaskResult formatResult = null;
                    for (JobResult jobResult : jobResults) {
                        formatResult = TaskResultFactory.transferTaskResult(taskResult.taskType, taskResult, jobResult.getFormat(), jobConfig);
                        if (formatResult != null) {
                            jobResult.addTaskResult(formatResult);
                        }
                    }
                }
            }
            executor.shutdownNow();

            for (JobResult jobResult : jobResults) {
                jobResult.output();
            }
            Log.d(TAG, "parse apk end, try to delete tmp un zip files");
            FileUtil.deleteDirectory(new File(jobConfig.getUnzipPath()));

        } catch (Exception e) {
            Log.e(TAG, "Task executor execute with error:" + e.getMessage());
            throw e;
        }
    }
}
