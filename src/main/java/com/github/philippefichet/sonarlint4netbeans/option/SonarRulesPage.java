/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.philippefichet.sonarlint4netbeans.option;

import java.util.List;
import org.sonarsource.sonarlint.shaded.org.sonarqube.ws.Rules;

/**
 *
 * @author Administrator
 */
public class SonarRulesPage {
    
    private int total;
    private int p;
    private int ps;
    List<Rule> rules;
   

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getP() {
        return p;
    }

    public void setP(int p) {
        this.p = p;
    }

    public int getPs() {
        return ps;
    }

    public void setPs(int ps) {
        this.ps = ps;
    }

    public List<Rule> getRules() {
        return rules;
    }

    public void setRules(List<Rule> rules) {
        this.rules = rules;
    }  
}
