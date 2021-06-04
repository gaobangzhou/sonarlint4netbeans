/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.philippefichet.sonarlint4netbeans.option;

/**
 *
 * @author Administrator
 */
public interface SonarQubeOptionsListener {

    public void sonarQubeOptionsChanged(String server, String profileName, String profileId);
}
