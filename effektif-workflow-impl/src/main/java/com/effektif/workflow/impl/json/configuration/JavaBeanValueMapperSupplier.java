/* Copyright (c) 2014, Effektif GmbH.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. */
package com.effektif.workflow.impl.json.configuration;

import com.effektif.workflow.impl.configuration.Brewery;
import com.effektif.workflow.impl.configuration.Supplier;
import com.effektif.workflow.impl.json.JavaBeanValueMapper;
import com.effektif.workflow.impl.json.Mappings;


/**
 * @author Tom Baeyens
 */
public class JavaBeanValueMapperSupplier implements Supplier {

  @Override
  public Object supply(Brewery brewery) {
    JavaBeanValueMappingsBuilder javaBeanValueMappingsBuilder = brewery.get(JavaBeanValueMappingsBuilder.class);
    Mappings mappings = javaBeanValueMappingsBuilder.getMappings();
    return new JavaBeanValueMapper(mappings);
  }

  @Override
  public boolean isSingleton() {
    return true;
  }
}
