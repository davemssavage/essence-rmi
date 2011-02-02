/*
 Copyright 2008 Peter Lawrey

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an &quot;AS IS&quot; BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package org.freshvanilla.rmi;

import java.io.Serializable;

import org.freshvanilla.lang.AbstractPojo;

public class WrapperPojo extends AbstractPojo implements Serializable {
    private static final long serialVersionUID = 1L;

    public Boolean booleanField;
    public Byte byteField;
    public Short shortField;
    public Character charField;
    public Integer intField;
    public Float floatField;
    public Long longField;
    public Double doubleField;
    public String stringField;

    public WrapperPojo(Boolean booleanField,
                       Byte byteField,
                       Short shortField,
                       Character charField,
                       Integer intField,
                       Float floatField,
                       Long longField,
                       Double doubleField,
                       String stringField) {
        this.booleanField = booleanField;
        this.byteField = byteField;
        this.shortField = shortField;
        this.charField = charField;
        this.intField = intField;
        this.floatField = floatField;
        this.longField = longField;
        this.doubleField = doubleField;
        this.stringField = stringField;
    }

}
