package org.hisp.dhis.integration.rapidpro.expression;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.hisp.dhis.integration.rapidpro.ProgramStageToFlowMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FlowUuidsExpr implements Expression
{
    @Autowired
    private ProgramStageToFlowMap programStageToFlowMap;

    @Value( "${rapidpro.flow.uuids:}" )
    private String aggregateReportFlowUuids;

    @Override
    public <T> T evaluate( Exchange exchange, Class<T> type )
    {
        String programStageFlowUuids = programStageToFlowMap.getFlowUuids();
        String flowUuids = (programStageFlowUuids.isEmpty() && aggregateReportFlowUuids.isEmpty()) ?
            "" :
            String.join( ",", programStageFlowUuids, aggregateReportFlowUuids );
        return (T) flowUuids;
    }

    public ProgramStageToFlowMap getProgramStageToFlowMap()
    {
        return programStageToFlowMap;
    }

    public void setProgramStageToFlowMap( ProgramStageToFlowMap programStageToFlowMap )
    {
        this.programStageToFlowMap = programStageToFlowMap;
    }

    public String getAggregateReportFlowUuids()
    {
        return aggregateReportFlowUuids;
    }

    public void setAggregateReportFlowUuids( String aggregateReportFlowUuids )
    {
        this.aggregateReportFlowUuids = aggregateReportFlowUuids;
    }
}
