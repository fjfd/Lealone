/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.codefollower.lealone.atomicdb.locator;

import java.net.InetAddress;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codefollower.lealone.atomicdb.db.SystemKeyspace;
import com.codefollower.lealone.atomicdb.exceptions.ConfigurationException;
import com.codefollower.lealone.atomicdb.gms.ApplicationState;
import com.codefollower.lealone.atomicdb.gms.EndpointState;
import com.codefollower.lealone.atomicdb.gms.Gossiper;
import com.codefollower.lealone.atomicdb.service.StorageService;
import com.codefollower.lealone.atomicdb.utils.FBUtilities;


public class GossipingPropertyFileSnitch extends AbstractNetworkTopologySnitch// implements IEndpointStateChangeSubscriber
{
    private static final Logger logger = LoggerFactory.getLogger(GossipingPropertyFileSnitch.class);

    private PropertyFileSnitch psnitch;
    private String myDC;
    private String myRack;
    private Map<InetAddress, Map<String, String>> savedEndpoints;
    private String DEFAULT_DC = "UNKNOWN_DC";
    private String DEFAULT_RACK = "UNKNOWN_RACK";
    private final boolean preferLocal;

    public GossipingPropertyFileSnitch() throws ConfigurationException
    {
        myDC = SnitchProperties.get("dc", null);
        myRack = SnitchProperties.get("rack", null);
        if (myDC == null || myRack == null)
            throw new ConfigurationException("DC or rack not found in snitch properties, check your configuration in: " + SnitchProperties.RACKDC_PROPERTY_FILENAME);

        myDC = myDC.trim();
        myRack = myRack.trim();
        preferLocal = Boolean.parseBoolean(SnitchProperties.get("prefer_local", "false"));
        try
        {
            psnitch = new PropertyFileSnitch();
            logger.info("Loaded {} for compatibility", PropertyFileSnitch.SNITCH_PROPERTIES_FILENAME);
        }
        catch (ConfigurationException e)
        {
            logger.info("Unable to load {}; compatibility mode disabled", PropertyFileSnitch.SNITCH_PROPERTIES_FILENAME);
        }
    }

    /**
     * Return the data center for which an endpoint resides in
     *
     * @param endpoint the endpoint to process
     * @return string of data center
     */
    public String getDatacenter(InetAddress endpoint)
    {
        if (endpoint.equals(FBUtilities.getBroadcastAddress()))
            return myDC;

        EndpointState epState = Gossiper.instance.getEndpointStateForEndpoint(endpoint);
        if (epState == null || epState.getApplicationState(ApplicationState.DC) == null)
        {
            if (psnitch == null)
            {
                if (savedEndpoints == null)
                    savedEndpoints = SystemKeyspace.loadDcRackInfo();
                if (savedEndpoints.containsKey(endpoint))
                    return savedEndpoints.get(endpoint).get("data_center");
                return DEFAULT_DC;
            }
            else
                return psnitch.getDatacenter(endpoint);
        }
        return epState.getApplicationState(ApplicationState.DC).value;
    }

    /**
     * Return the rack for which an endpoint resides in
     *
     * @param endpoint the endpoint to process
     * @return string of rack
     */
    public String getRack(InetAddress endpoint)
    {
        if (endpoint.equals(FBUtilities.getBroadcastAddress()))
            return myRack;

        EndpointState epState = Gossiper.instance.getEndpointStateForEndpoint(endpoint);
        if (epState == null || epState.getApplicationState(ApplicationState.RACK) == null)
        {
            if (psnitch == null)
            {
                if (savedEndpoints == null)
                    savedEndpoints = SystemKeyspace.loadDcRackInfo();
                if (savedEndpoints.containsKey(endpoint))
                    return savedEndpoints.get(endpoint).get("rack");
                return DEFAULT_RACK;
            }
            else
                return psnitch.getRack(endpoint);
        }
        return epState.getApplicationState(ApplicationState.RACK).value;
    }

    public void gossiperStarting()
    {
        super.gossiperStarting();
        Gossiper.instance.addLocalApplicationState(ApplicationState.INTERNAL_IP,
                                                   StorageService.instance.valueFactory.internalIP(FBUtilities.getLocalAddress().getHostAddress()));
        Gossiper.instance.register(new ReconnectableSnitchHelper(this, myDC, preferLocal));
    }
}
