/*
 * Copyright (c) 2004-2022, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the HISP project nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.hisp.dhis.integration.rapidpro;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProgramStageToFlowMapTestCase
{
    private ProgramStageToFlowMap programStageToFlowMap = new ProgramStageToFlowMap();

    @BeforeEach
    public void beforeEach()
    {
        programStageToFlowMap.add( "ZP5HZ87wzc0", "specimen-collection-flow-uuid" );
        programStageToFlowMap.add( "Ish2wk3eLg3", "laboratory-testing-flow-uuid" );
    }

    @AfterEach
    public void afterEach()
    {
        programStageToFlowMap.clear();
    }

    @Test
    public void testWhenMapIsSetThenItShouldContainTwoKeys()
    {
        assertEquals( 2, programStageToFlowMap.getMap().size() );
    }

    @Test
    public void testWhenGetFlowUuidsCalledWithProgramStageIdThenItShouldReturnCorrectUuid()
    {
        String programStageId = "ZP5HZ87wzc0";
        String expectedFlowUuid = "specimen-collection-flow-uuid";

        assertEquals( expectedFlowUuid, programStageToFlowMap.getFlowUuids( programStageId ) );
    }

    @Test
    public void testWhenGetFlowUuidsCalledWithBodyThenItShouldReturnCorrectUuid()
    {
        Map<String, Object> body = new HashMap<>();
        body.put( "programStage", "Ish2wk3eLg3" );
        String expectedFlowUuid = "laboratory-testing-flow-uuid";

        assertEquals( expectedFlowUuid, programStageToFlowMap.getFlowUuids( body ) );
    }

    @Test
    public void testWhenGetAllProgramStageIdsCalledThenItShouldReturnAllKeys()
    {
        List<String> programStageIds = programStageToFlowMap.getAllProgramStages();

        assertTrue( programStageIds.contains( "ZP5HZ87wzc0" ) );
        assertTrue( programStageIds.contains( "Ish2wk3eLg3" ) );
        assertEquals( 2, programStageIds.size() );
    }

    @Test
    public void testWhenClearCalledThenMapShouldBeEmpty()
    {
        programStageToFlowMap.clear();

        assertTrue( programStageToFlowMap.getMap().isEmpty() );
    }
}
