/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,  
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.arquillian.container.mss.extension.lifecycle;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.jboss.arquillian.config.descriptor.api.ArquillianDescriptor;
import org.jboss.arquillian.config.descriptor.api.ContainerDef;
import org.jboss.arquillian.container.mobicents.api.annotations.ConcurrencyControlMode;
import org.jboss.arquillian.container.mobicents.api.annotations.ContextParam;
import org.jboss.arquillian.container.mobicents.api.annotations.ContextParamMap;
import org.jboss.arquillian.container.spi.client.container.DeployableContainer;
import org.jboss.arquillian.container.spi.event.container.AfterDeploy;
import org.jboss.arquillian.container.spi.event.container.AfterSetup;
import org.jboss.arquillian.container.spi.event.container.AfterUnDeploy;
import org.jboss.arquillian.container.spi.event.container.BeforeDeploy;
import org.jboss.arquillian.container.spi.event.container.BeforeUnDeploy;
import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.arquillian.core.api.Instance;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.core.api.annotation.Observes;
import org.jboss.arquillian.test.spi.TestClass;
import org.jboss.arquillian.test.spi.event.suite.After;
import org.jboss.arquillian.test.spi.event.suite.Before;

/**
 * LifecycleExecuter. Responsible to start/stop the container passing extra context parameters that could be present
 * in the <code>@ContextParam</code>, <code>ContextParamMap</code> or <code>ConcurrencyControlMode</code> annotations
 *
 * @author <a href="mailto:aslak@redhat.com">Aslak Knutsen</a>
 * @version $Revision: $
 */
public class LifecycleExecuter
{

	//Using these events, we can get access to the deployableContainer so we can start/stop, deploy/undeploy, 
	//setup with a new configuration and also we have access to the deployment

	public void executeBeforeDeploy(@Observes BeforeDeploy event, TestClass testClass)
	{
		execute(
				testClass.getMethods(
						org.jboss.arquillian.container.mobicents.api.annotations.BeforeDeploy.class));
	}

	public void executeAfterDeploy(@Observes AfterDeploy event, TestClass testClass) throws IllegalArgumentException, IllegalAccessException
	{
		execute(
				testClass.getMethods(
						org.jboss.arquillian.container.mobicents.api.annotations.AfterDeploy.class));
	}

	public void executeBeforeUnDeploy(@Observes BeforeUnDeploy event, TestClass testClass)
	{
		execute(
				testClass.getMethods(
						org.jboss.arquillian.container.mobicents.api.annotations.BeforeUnDeploy.class));
	}

	public void executeAfterUnDeploy(@Observes AfterUnDeploy event, TestClass testClass) throws IllegalArgumentException, IllegalAccessException
	{
		execute(
				testClass.getMethods(
						org.jboss.arquillian.container.mobicents.api.annotations.AfterUnDeploy.class));
	}

	private DeployableContainer<?> deployableContainer;
	private Object testInstance;

	/*
	 * #1 Observe Container's AfterSetup to get the DeployableContainer 
	 */
	public void executeAfterSetup(@Observes AfterSetup event){
		deployableContainer = event.getDeployableContainer();
	}

	/*
	 * Remember that for the ContainerController to be able to control the container, the container have to be  
	 * defined with mode="manual" at the arquillian.xml
	 * 
	 * <container qualifier="mss-tomcat-embedded-6" default="true" mode="manual">
	 *	<configuration>
	 *	.....
	 *	</configuration>
	 * 
	 */

	@Inject
	private Instance<ArquillianDescriptor> descriptor;
	@Inject
	private Instance<ContainerController> containerController;

	private Annotation contextParam; 
	private Annotation contextParamMap; 
	private Annotation concurencyControl;

	// #3
	public void executeBeforeTest(@Observes Before event, TestClass testClass) throws IllegalArgumentException, IllegalAccessException{

		boolean paramsEmpty = false;
		testInstance = event.getTestInstance();

		Map<String, String> parameters = new HashMap<String, String>();
		List<ContainerDef> containerDefs = descriptor.get().getContainers();
		Iterator<ContainerDef> iter = containerDefs.iterator();

		if (checkForAnnotations(event))
			parameters = getParameters(event);

		//Workaround for https://community.jboss.org/thread/178021
		if (parameters.isEmpty()){
			parameters = resetParameters();
			paramsEmpty = true;
		}

		while (iter.hasNext()){
			ContainerDef containerDef = (ContainerDef) iter.next();
			String containerName = containerDef.getContainerName();
			boolean containerInManualMode = containerDef.getMode().equalsIgnoreCase("manual");

			//If the container is not in manual mode then we cannot pass any parameters and control it
			if (!containerInManualMode && !paramsEmpty){
				//				return;
				throw new UnsupportedOperationException("Container not in manual mode");
			}

			if (containerInManualMode) {
				if (paramsEmpty){
					//Workaround for https://community.jboss.org/thread/178021
					//Have to pass reseted parameters again, otherwise they are the same from the previous test
					containerController.get().start(containerName,parameters);
				} else {
					containerController.get().start(containerName,parameters);
				}
			}
		}

	}


	private Map<String, String> resetParameters() {

		Map<String, String> parameters = new HashMap<String, String>();
		String paramSeparator = "---";
		String valueSeparator = "-%%-";		
		parameters.put("paramSeparator",paramSeparator);
		parameters.put("valueSeparator", valueSeparator);

		String paramName = "ignore_"+UUID.randomUUID().toString();
		String paramValue = "ignore_"+UUID.randomUUID().toString();
		
		parameters.put("contextParam", paramName+valueSeparator+paramValue);

		return parameters;
	}

	public void executeAfterTest(@Observes After event, TestClass testClass) throws IllegalArgumentException, IllegalAccessException{

		List<ContainerDef> containerDefs = descriptor.get().getContainers();
		Iterator<ContainerDef> iter = containerDefs.iterator();

		while (iter.hasNext()){
			ContainerDef containerDef = (ContainerDef) iter.next();

			//If the container is not in manual mode then we cannot pass any parameters and control it
			if (!containerDef.getMode().equalsIgnoreCase("manual")){
				return;
				//throw new UnsupportedOperationException("Container not in manual mode");
			}

			String containerName = containerDef.getContainerName();
			containerController.get().stop(containerName);
		}
	}

	private boolean checkForAnnotations(Before event) {
		Boolean result = false;

		contextParam = event.getTestMethod().getAnnotation(org.jboss.arquillian.container.mobicents.api.annotations.ContextParam.class);
		contextParamMap = event.getTestMethod().getAnnotation(org.jboss.arquillian.container.mobicents.api.annotations.ContextParamMap.class);
		concurencyControl = event.getTestMethod().getAnnotation(org.jboss.arquillian.container.mobicents.api.annotations.ConcurrencyControlMode.class);

		if (contextParam != null || contextParamMap != null || concurencyControl != null)
			result = true;

		return result;
	}

	private Map<String, String> getParameters(Before event) {

		String contextParamName = null;
		String contextParamValue = null;

		Map<String, String> parameters = new HashMap<String, String>();
		String paramSeparator = "---";
		String valueSeparator = "-%%-";		
		parameters.put("paramSeparator",paramSeparator);
		parameters.put("valueSeparator", valueSeparator);

		HashMap<String, String> paramMap = new HashMap<String, String>();

		if (contextParamMap != null){
			String mapName = ((ContextParamMap)contextParamMap).value();
			Field[] fields = event.getTestClass().getJavaClass().getDeclaredFields(); 
			for (Field field : fields) {
				if (field.getAnnotation(org.jboss.arquillian.container.mobicents.api.annotations.ContextParamMap.class) != null 
						&& field.getAnnotation(org.jboss.arquillian.container.mobicents.api.annotations.ContextParamMap.class).value().equals(mapName) ){
					try {
						Boolean flag = field.isAccessible();
						field.setAccessible(true);
						paramMap = (HashMap<String, String>) field.get(event.getTestInstance());
						field.setAccessible(flag);
					} catch (IllegalArgumentException e) {
						e.printStackTrace();
					} catch (IllegalAccessException e) {
						e.printStackTrace();
					}
				} 
			}

			//Throw exception if after scanning all the fields we don't find the map we are looking for.
			if (paramMap.isEmpty())
				throw new UnsupportedOperationException("There is no declared field in the class with annotation " +
						"@ContextParamΜap(\""+mapName+"\") as in the test method: "+event.getTestMethod().getName());
		}

		StringBuffer values = new StringBuffer();

		if (contextParam != null){
			contextParamName = ((ContextParam)contextParam).name();
			contextParamValue = ((ContextParam)contextParam).value();
			values.append(contextParamName+valueSeparator+contextParamValue+paramSeparator);
		}
		if (!paramMap.isEmpty()){
			Iterator<String> iterator = paramMap.keySet().iterator();

			String firstKey = iterator.next();
			values.append(firstKey+valueSeparator+paramMap.get(firstKey));

			while(iterator.hasNext()){
				String key = iterator.next();
				values.append(paramSeparator+key+valueSeparator+paramMap.get(key));
			}
		}

		if (contextParam != null || !paramMap.isEmpty() )
			parameters.put("contextParam", values.toString());

		if(concurencyControl != null){
			parameters.put("concurrencyControl", ((ConcurrencyControlMode)concurencyControl).value().toString());
		}

		return parameters;
	}

	private void execute(Method[] methods)
	{
		if(methods == null)
		{
			return;
		}
		for(Method method : methods)
		{
			try
			{
				method.invoke(null);
			}
			catch (Exception e) 
			{
				throw new RuntimeException("Could not execute method: " + method, e);
			}
		}
	}
}