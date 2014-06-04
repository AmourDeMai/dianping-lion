package com.dianping.lion.api.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.dianping.lion.api.exception.SecurityException;
import com.dianping.lion.entity.Config;
import com.dianping.lion.entity.ConfigInstance;
import com.dianping.lion.entity.ConfigTypeEnum;
import com.dianping.lion.entity.OperationLog;
import com.dianping.lion.entity.OperationTypeEnum;
import com.dianping.lion.entity.Project;
import com.dianping.lion.service.ConfigService;
import com.dianping.lion.service.ProjectService;

@Controller
@RequestMapping("/config2")
public class ConfigController extends BaseController {

    @Autowired
    private ConfigService configService;
    
    @Autowired
    private ProjectService projectService;
    
    @RequestMapping(value = "/create", method = RequestMethod.GET)
    @ResponseBody
    public Result create(@RequestParam(value="id") int id, 
                         @RequestParam(value="project") String project,
                         @RequestParam(value="key") String key, 
                         @RequestParam(value="desc", required=false, defaultValue="") String desc) {
        try {
            verifyIdentity(id);
        } catch (SecurityException e) {
            return Result.createErrorResult(e.getMessage());
        }
        
        Project prj = projectService.findProject(project);
        if(prj == null) {
            return Result.createErrorResult(String.format("Project %s does not exist", project));
        }
        
        Config config = configService.findConfigByKey(key);
        if(config != null) {
            return Result.createErrorResult(String.format("Config %s already exists", key));
        }
        
        config = new Config();
        config.setKey(key);
        config.setDesc(desc);
        config.setTypeEnum(ConfigTypeEnum.String);
        config.setProjectId(prj.getId());
        configService.createConfig(config);
        String message = String.format("Created config %s in project %s", key, project);
        opLogService.createOpLog(new OperationLog(OperationTypeEnum.Config_Add, prj.getId(), message));
        return Result.createSuccessResult(message);
    }
    
    @RequestMapping(value = "/set", method = RequestMethod.GET)
    @ResponseBody
    public Result set(@RequestParam(value="id") int id, 
                      @RequestParam(value="env") String env,
                      @RequestParam(value="key") String key,
                      @RequestParam(value="group", required=false, defaultValue="") String group,
                      @RequestParam(value="value") String value) {
        try {
            verifyIdentity(id);
        } catch (SecurityException e) {
            return Result.createErrorResult(e.getMessage());
        }
        
        int envId = getEnvId(env);
        if(envId == -1) {
            return Result.createErrorResult("Invalid environment " + env);
        }
        
        Config config = configService.findConfigByKey(key);
        if(config == null) {
            return Result.createErrorResult(String.format("Config %s does not exist", key));
        }
        
        configService.setConfigValue(config.getId(), envId, group, value);
        String message = String.format("Set config %s in env %s group [%s] to %s", key, env, group, value);
        opLogService.createOpLog(new OperationLog(OperationTypeEnum.Config_Edit, message));
        return Result.createSuccessResult(message);
    }
    
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    @ResponseBody
    public Result list(@RequestParam(value="prefix") String prefix) {
        if(prefix==null || prefix.length() < 5) {
            return Result.createErrorResult("Prefix is too short");
        }
        
        List<Config> configList = configService.findConfigByPrefix(prefix);
        List<String> keyList = new ArrayList<String>();
        for(Config config : configList) {
            keyList.add(config.getKey());
        }
        
        return Result.createSuccessResult(keyList);
    }
    
    @RequestMapping(value = "/get", method = RequestMethod.GET)
    @ResponseBody
    public Result get(@RequestParam(value="id") int id, 
                      @RequestParam(value="env") String env,
                      @RequestParam(value="key", required=false) String key,
                      @RequestParam(value="keys", required=false) String keys,
                      @RequestParam(value="prefix", required=false) String prefix,
                      @RequestParam(value="group", required=false, defaultValue="") String group) {
        try {
            verifyIdentity(id);
        } catch (SecurityException e) {
            return Result.createErrorResult(e.getMessage());
        }
        
        int envId = getEnvId(env);
        if(envId == -1) {
            return Result.createErrorResult("Invalid environment " + env);
        }

        Result result = null;
        Object object = null;
        
        if(StringUtils.isNotBlank(key)) {
            object = getConfig(envId, key, group);
            result = Result.createSuccessResult(object);
        } else if(StringUtils.isNotBlank(keys)) {
            object = getConfigs(envId, keys, group);
            result = Result.createSuccessResult(object);
        } else if(StringUtils.isNotBlank(prefix)) {
            if(prefix==null || prefix.length() < 5) {
                result = Result.createErrorResult("Prefix is too short");
            } else {
                object = getConfigsByPrefix(envId, prefix, group);
                result = Result.createSuccessResult(object);
            }
        } else {
            result = Result.createErrorResult("Key is null");
        }
        
        return result;
    }
    
    private String getConfig(int envId, String key, String group) {
        ConfigInstance ci = configService.findInstance(key, envId, group);
        return ci.getValue();
    }
    
    private Map<String, String> getConfigs(int envId, String keys, String group) {
        List<String> keyList = convertToList(keys);
        List<ConfigInstance> ciList = configService.findInstancesByKeys(keyList, envId, group);
        
        Map<String, String> keyValue = new HashMap<String, String>();
        for(ConfigInstance ci : ciList) {
            keyValue.put(ci.getRefkey(), ci.getValue());
        }
        return keyValue;
    }
    
    private Map<String, String> getConfigsByPrefix(int envId, String prefix, String group) {
        List<ConfigInstance> ciList = configService.findInstancesByPrefix(prefix, envId, group);
        
        Map<String, String> keyValue = new HashMap<String, String>();
        for(ConfigInstance ci : ciList) {
            keyValue.put(ci.getRefkey(), ci.getValue());
        }
        return keyValue;
    }
    
    private List<String> convertToList(String keys) {
        if(StringUtils.isEmpty(keys)) 
            return Collections.emptyList();
        
        String[] keyArray = keys.split(",");
        List<String> keyList = new ArrayList<String>();
        for(String key : keyArray) {
            key = key.trim();
            if(!key.isEmpty())
                keyList.add(key);
        }
        return keyList;
    }
}
