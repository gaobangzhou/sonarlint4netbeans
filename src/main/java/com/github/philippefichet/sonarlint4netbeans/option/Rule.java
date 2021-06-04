/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.philippefichet.sonarlint4netbeans.option;

import java.util.List;

/**
 *
 * @author Administrator
 */
public class Rule {
    private String key;
    private String name;
    private String serverity;  
    private String lang;
     List<RuleParam> params;

    public Rule() {
    }

    public Rule(String key, String name) {
        this.key = key;
        this.name = name;
    }
     
    

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getServerity() {
        return serverity;
    }

    public void setServerity(String serverity) {
        this.serverity = serverity;
    }

    public List<RuleParam> getParams() {
        return params;
    }

    public void setParams(List<RuleParam> params) {
        this.params = params;
    }

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }
    
}
