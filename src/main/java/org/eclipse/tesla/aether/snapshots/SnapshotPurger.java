package org.eclipse.tesla.aether.snapshots;

/*******************************************************************************
 * Copyright (c) 2011, 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

import java.io.File;
import java.util.Map;
import java.util.regex.Pattern;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.aether.AbstractRepositoryListener;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositoryListener;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.spi.log.Logger;
import org.eclipse.aether.spi.log.LoggerFactory;
import org.eclipse.aether.spi.log.NullLoggerFactory;

/**
 * A repository listener which purges old snapshots from the local repository when a new snapshot was downloaded.
 */
@Component( role = RepositoryListener.class )
public class SnapshotPurger
    extends AbstractRepositoryListener
{

    static final String PROPERTY_EXCLUDES = "tesla.snapshotPurger.excludes";

    @Requirement( role = LoggerFactory.class )
    private Logger logger = NullLoggerFactory.LOGGER;

    SnapshotPurger setLoggerFactory( LoggerFactory loggerFactory )
    {
        this.logger = NullLoggerFactory.getSafeLogger( loggerFactory, getClass() );
        return this;
    }

    void setLogger( LoggerFactory loggerFactory )
    {
        // plexus support
        setLoggerFactory( loggerFactory );
    }

    @Override
    public void artifactDownloaded( RepositoryEvent event )
    {
        File artifactFile = event.getFile();
        if ( artifactFile == null )
        {
            return;
        }
        File artifactDir = artifactFile.getParentFile();

        Artifact artifact = event.getArtifact();

        String snapshotName = artifactFile.getName();
        snapshotName = snapshotName.replace( artifact.getVersion(), artifact.getBaseVersion() );
        int snapshot = snapshotName.lastIndexOf( "SNAPSHOT" );
        if ( snapshot >= 0 )
        {
            if ( isExcluded( artifact, event.getSession().getConfigProperties() ) )
            {
                logger.debug( "Not purging old snapshots of " + artifact + " as per configuration" );
                return;
            }

            String snapshotRegex =
                "\\Q" + snapshotName.substring( 0, snapshot ) + "\\E" + "([0-9]{8}.[0-9]{6}-[0-9]+)" + "\\Q"
                    + snapshotName.substring( snapshot + 8 ) + "\\E" + "(\\.(md5|sha1|asc|lastUpdated))?";

            Pattern snapshotPattern = Pattern.compile( snapshotRegex );

            String[] snapshotFiles = artifactDir.list();
            if ( snapshotFiles != null )
            {
                for ( String snapshotFile : snapshotFiles )
                {
                    if ( !snapshotPattern.matcher( snapshotFile ).matches() )
                    {
                        continue;
                    }
                    if ( snapshotFile.contains( artifact.getVersion() ) )
                    {
                        continue;
                    }

                    File oldFile = new File( artifactDir, snapshotFile );
                    if ( oldFile.delete() )
                    {
                        logger.debug( "Purged old snapshot artifact " + oldFile );
                    }
                    else if ( oldFile.exists() )
                    {
                        logger.debug( "Failed to purge old snapshot artifact " + oldFile );
                    }
                }
            }
            else
            {
                logger.debug( "Failed to scan for old snapshot artifacts in " + artifactDir );
            }
        }
    }

    boolean isExcluded( Artifact artifact, Map<String, ?> config )
    {
        Object v = config.get( PROPERTY_EXCLUDES );
        if ( !( v instanceof String ) )
        {
            return false;
        }
        String[] patterns = v.toString().split( ",+" );
        for ( String pattern : patterns )
        {
            pattern = pattern.trim();
            if ( isMatched( artifact, pattern ) )
            {
                return true;
            }
        }
        return false;
    }

    boolean isMatched( Artifact artifact, String pattern )
    {
        String[] coords = pattern.split( ":" );
        if ( !isMatch( artifact.getGroupId(), coords[0] ) )
        {
            return false;
        }
        if ( coords.length > 1 && !isMatch( artifact.getArtifactId(), coords[1] ) )
        {
            return false;
        }
        return coords.length <= 2;
    }

    boolean isMatch( String coord, String pattern )
    {
        StringBuilder regex = new StringBuilder( 128 );
        for ( int i = 0; i < pattern.length(); i++ )
        {
            char c = pattern.charAt( i );
            switch ( c )
            {
                case '?':
                    regex.append( '.' );
                    break;
                case '*':
                    regex.append( ".*" );
                    break;
                default:
                    if ( !Character.isLetterOrDigit( c ) )
                    {
                        regex.append( '\\' );
                    }
                    regex.append( c );
            }
        }
        return coord.matches( regex.toString() );
    }

}
