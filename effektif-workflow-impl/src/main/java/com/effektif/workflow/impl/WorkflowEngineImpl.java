/* Copyright 2014 Effektif GmbH.
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
package com.effektif.workflow.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.effektif.workflow.api.WorkflowEngine;
import com.effektif.workflow.api.command.Message;
import com.effektif.workflow.api.command.RequestContext;
import com.effektif.workflow.api.command.Start;
import com.effektif.workflow.api.query.WorkflowInstanceQuery;
import com.effektif.workflow.api.query.WorkflowQuery;
import com.effektif.workflow.api.workflow.Workflow;
import com.effektif.workflow.api.workflowinstance.WorkflowInstance;
import com.effektif.workflow.impl.activities.CallerReference;
import com.effektif.workflow.impl.json.JsonService;
import com.effektif.workflow.impl.plugin.Initializable;
import com.effektif.workflow.impl.plugin.ServiceRegistry;
import com.effektif.workflow.impl.util.Time;
import com.effektif.workflow.impl.workflow.ActivityImpl;
import com.effektif.workflow.impl.workflow.WorkflowImpl;
import com.effektif.workflow.impl.workflowinstance.ActivityInstanceImpl;
import com.effektif.workflow.impl.workflowinstance.LockImpl;
import com.effektif.workflow.impl.workflowinstance.WorkflowInstanceImpl;

public class WorkflowEngineImpl implements WorkflowEngine, Initializable {

  public static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);

  public String id;
  public ExecutorService executorService;
  public WorkflowCache workflowCache;
  public WorkflowStore workflowStore;
  public WorkflowInstanceStore workflowInstanceStore;
  public JsonService jsonService;
  public ServiceRegistry serviceRegistry;
  public List<WorkflowInstanceEventListener> listeners;

  @Override
  public void initialize(ServiceRegistry serviceRegistry, WorkflowEngineConfiguration configuration) {
    this.id = configuration.getId();
    this.jsonService = serviceRegistry.getService(JsonService.class);
    this.executorService = serviceRegistry.getService(ExecutorService.class);
    this.workflowCache = serviceRegistry.getService(WorkflowCache.class);
    this.workflowStore = serviceRegistry.getService(WorkflowStore.class);
    this.workflowInstanceStore = serviceRegistry.getService(WorkflowInstanceStore.class);
    this.serviceRegistry = serviceRegistry;
    this.listeners = new ArrayList<>();
  }
  
  public void startup() {
  }

  public void shutdown() {
    executorService.shutdown();
  }

  /// Workflow methods ////////////////////////////////////////////////////////////
  
  @Override
  public Workflow deployWorkflow(Workflow workflowApi) {
    if (log.isDebugEnabled()) {
      log.debug("Deploying workflow");
    }
    RequestContext requestContext = RequestContext.current();
    if (requestContext!=null) {
      workflowApi.deployedBy(requestContext.getAuthenticatedUserId());
      workflowApi.organizationId(requestContext.getOrganizationId());
    }
    WorkflowParser parser = WorkflowParser.parse(this, workflowApi);
    if (!parser.hasErrors()) {
      WorkflowImpl workflowImpl = parser.getWorkflow();
      String workflowId; 
      if (workflowApi.getId()==null) {
        workflowId = workflowStore.generateWorkflowId();
        workflowApi.setId(workflowId);
      }
      workflowImpl.id = workflowApi.getId();
      workflowStore.insertWorkflow(workflowApi, workflowImpl);
      workflowCache.put(workflowImpl);
    } else {
      throw new RuntimeException(parser.issues.getIssueReport());
    }
    return workflowApi;
  }

  @Override
  public Workflow validateWorkflow(Workflow workflowApi) {
    if (log.isDebugEnabled()) {
      log.debug("Validating workflow");
    }
    RequestContext requestContext = RequestContext.current();
    if (requestContext!=null) {
      workflowApi.deployedBy(requestContext.getAuthenticatedUserId());
      workflowApi.organizationId(requestContext.getOrganizationId());
    }
    WorkflowParser.parse(this, workflowApi);
    return workflowApi;
  }

  @Override
  public List<Workflow> findWorkflows(WorkflowQuery workflowQuery) {
    return workflowStore.findWorkflows(workflowQuery);
  }
  
  @Override
  public void deleteWorkflows(WorkflowQuery workflowQuery) {
    workflowStore.deleteWorkflows(workflowQuery);
  }

  @Override
  public WorkflowInstance startWorkflowInstance(Workflow workflow) {
    String workflowId = workflow.getId();
    if (workflowId==null) {
      throw new RuntimeException("Please ensure that the given workflow is deployed first");
    }
    return startWorkflowInstance(new Start().workflowId(workflowId), null);
  }

  public WorkflowInstance startWorkflowInstance(Start startCommand) {
    return startWorkflowInstance(startCommand, null);
  }

  /** caller has to ensure that start.variableValues is not serialized @see VariableRequestImpl#serialize & VariableRequestImpl#deserialize */
  public WorkflowInstance startWorkflowInstance(Start startCommand, CallerReference callerReference) {
    String workflowId = startCommand.getWorkflowId();
    if (workflowId==null) {
      if (startCommand.getWorkflowName()!=null) {
        workflowId = workflowStore.findLatestWorkflowIdByName(startCommand.getWorkflowName());
        if (workflowId==null) throw new RuntimeException("No workflow found for name '"+startCommand.getWorkflowName()+"'");
      } else {
        throw new RuntimeException("No workflow specified");
      }
    }

    WorkflowImpl workflow = getWorkflowImpl(workflowId);
    String workflowInstanceId = workflowInstanceStore.generateWorkflowInstanceId();
    WorkflowInstanceImpl workflowInstance = new WorkflowInstanceImpl(this, workflow, workflowInstanceId);
    if (callerReference!=null) {
      workflowInstance.callerWorkflowInstanceId = callerReference.callerWorkflowInstanceId;
      workflowInstance.callerActivityInstanceId = callerReference.callerActivityInstanceId;
    }
    workflowInstance.setVariableValues(startCommand.getVariableValues());
    if (log.isDebugEnabled()) log.debug("Starting "+workflowInstance);
    workflowInstance.start = Time.now();
    if (workflow.startActivities!=null) {
      for (ActivityImpl startActivityDefinition: workflow.startActivities) {
        workflowInstance.execute(startActivityDefinition);
      }
    }
    LockImpl lock = new LockImpl();
    lock.setTime(Time.now());
    lock.setOwner(getId());
    workflowInstance.setLock(lock);
    workflowInstanceStore.insertWorkflowInstance(workflowInstance);
    workflowInstance.executeWork();
    return workflowInstance.toWorkflowInstance();
  }
  
  @Override
  public WorkflowInstance sendMessage(Message message) {
    WorkflowInstanceImpl workflowInstance = lockProcessInstanceWithRetry(message.getWorkflowInstanceId(), message.getActivityInstanceId());
    workflowInstance.setVariableValues(message.getVariableValues());
    ActivityInstanceImpl activityInstance = workflowInstance.findActivityInstance(message.getActivityInstanceId());
    if (activityInstance.isEnded()) {
      throw new RuntimeException("Activity instance "+activityInstance+" is already ended");
    }
    if (log.isDebugEnabled())
      log.debug("Signalling "+activityInstance);
    ActivityImpl activity = activityInstance.getActivity();
    activity.activityType.message(activityInstance);
    workflowInstance.executeWork();
    return workflowInstance.toWorkflowInstance();
  }

  @Override
  public void deleteWorkflowInstances(WorkflowInstanceQuery query) {
    workflowInstanceStore.deleteWorkflowInstances(query);
  }

  @Override
  public List<WorkflowInstance> findWorkflowInstances(WorkflowInstanceQuery query) {
    List<WorkflowInstanceImpl> workflowInstances = workflowInstanceStore.findWorkflowInstances(query);
    return WorkflowInstanceImpl.toWorkflowInstances(workflowInstances);
  }
  
  /** retrieves the executable form of the workflow using the workflow cache */
  public WorkflowImpl getWorkflowImpl(String workflowId) {
    WorkflowImpl workflowImpl = workflowCache.get(workflowId);
    if (workflowImpl==null) {
      Workflow workflow = workflowStore.loadWorkflowById(workflowId);
      workflowImpl = WorkflowParser.parse(this, workflow)
        .checkNoErrors() // throws runtime exception if there are errors
        .getWorkflow();
      workflowCache.put(workflowImpl);
    }
    return workflowImpl;
  }
  
  public WorkflowInstanceImpl lockProcessInstanceWithRetry(
          final String workflowInstanceId, 
          final String activityInstanceId) {
    Retry<WorkflowInstanceImpl> retry = new Retry<WorkflowInstanceImpl>() {
      @Override
      public WorkflowInstanceImpl tryOnce() {
        return workflowInstanceStore.lockWorkflowInstance(workflowInstanceId, activityInstanceId);
      }
      @Override
      protected void failedWaitingForRetry() {
        if (log.isDebugEnabled()) {
          log.debug("Locking workflow instance "+workflowInstanceId+" failed... retrying in "+wait+" millis");
        }
      }
      @Override
      protected void interrupted() {
        if (log.isDebugEnabled()) {
          log.debug("Waiting for workflow instance lock was interrupted");
        }
      }
      @Override
      protected void failedPermanent() {
        throw new RuntimeException("Couldn't lock process instance with workflowInstanceId="+workflowInstanceId+" and activityInstanceId="+activityInstanceId);
      }
    };
    return retry.tryManyTimes();
  }

  public String getId() {
    return id;
  }

  public ServiceRegistry getServiceRegistry() {
    return serviceRegistry;
  }
  
  public JsonService getJsonService() {
    return jsonService;
  }
  
  public ExecutorService getExecutorService() {
    return executorService;
  }

  public WorkflowCache getProcessDefinitionCache() {
    return workflowCache;
  }

  public WorkflowStore getWorkflowStore() {
    return workflowStore;
  }
  
  public WorkflowInstanceStore getWorkflowInstanceStore() {
    return workflowInstanceStore;
  }

  public void addListener(WorkflowInstanceEventListener listener) {
    synchronized (listener) {
      listeners.add(listener);
    }
  }

  public void removeListener(WorkflowInstanceEventListener listener) {
    synchronized (listener) {
      listeners.remove(listener);
    }
  }

  public List<WorkflowInstanceEventListener> getListeners() {
    return Collections.unmodifiableList(listeners);
  }

  @Override
  public WorkflowEngine createWorkflowEngine(RequestContext requestContext) {
    return new ContextualWorkflowEngine(this, requestContext);
  }
}