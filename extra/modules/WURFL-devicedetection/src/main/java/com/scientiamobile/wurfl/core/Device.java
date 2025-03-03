package com.scientiamobile.wurfl.core;

import com.scientiamobile.wurfl.core.exc.VirtualCapabilityNotDefinedException;
import com.scientiamobile.wurfl.core.exc.CapabilityNotDefinedException;
import com.scientiamobile.wurfl.core.matchers.MatchType;

public interface Device {

    String getId();

    MatchType getMatchType();

    String getCapability(String name) throws CapabilityNotDefinedException;

    String getVirtualCapability(String name)
            throws VirtualCapabilityNotDefinedException, CapabilityNotDefinedException;

    int getVirtualCapabilityAsInt(String s) throws VirtualCapabilityNotDefinedException,
            CapabilityNotDefinedException, NumberFormatException;

    boolean getVirtualCapabilityAsBool(String vcapName) throws VirtualCapabilityNotDefinedException,
            CapabilityNotDefinedException, NumberFormatException;

    String getWURFLUserAgent();

    int getCapabilityAsInt(String capName) throws CapabilityNotDefinedException, NumberFormatException;

    boolean getCapabilityAsBool(String capName) throws CapabilityNotDefinedException, NumberFormatException;
}
