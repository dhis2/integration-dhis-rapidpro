package org.hisp.dhis.integration.rapidpro;

public class Dhis2ToRapidProException extends Exception
{
    public Dhis2ToRapidProException()
    {
        super();
    }

    public Dhis2ToRapidProException( String message )
    {
        super( message );
    }

    public Dhis2ToRapidProException( String message, Throwable cause )
    {
        super( message, cause );
    }

    public Dhis2ToRapidProException( Throwable cause )
    {
        super( cause );
    }

    protected Dhis2ToRapidProException( String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace )
    {
        super( message, cause, enableSuppression, writableStackTrace );
    }
}
