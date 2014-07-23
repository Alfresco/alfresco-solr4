/*
 * Copyright (C) 2005-2010 Alfresco Software Limited.
 *
 * This file is part of Alfresco
 *
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 */
package org.alfresco.solr.tracker;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generic Solr tracker job, allowing Quartz to initiate an index update from
 * a {@link Tracker} regardless of specific implementation.
 * 
 * TODO: type parameterisation may be unecessary.
 * 
 * @author Matt Ward
 */
public class TrackerJob<T extends Tracker> implements Job
{
    protected final static Logger log = LoggerFactory.getLogger(TrackerJob.class);

    public TrackerJob()
    {
        super();
    }

    /*
     * (non-Javadoc)
     * @see org.quartz.Job#execute(org.quartz.JobExecutionContext)
     */
    @Override
    public void execute(JobExecutionContext jec) throws JobExecutionException
    {
        // TODO: check TRACKER is instance of Tracker
        T coreTracker = (T) jec.getJobDetail().getJobDataMap().get("TRACKER");
        coreTracker.track();
    }
}