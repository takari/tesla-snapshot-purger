package org.eclipse.tesla.aether.snapshots;

/*******************************************************************************
 * Copyright (c) 2011 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

import java.io.File;
import java.util.regex.Pattern;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.sonatype.aether.AbstractRepositoryListener;
import org.sonatype.aether.RepositoryEvent;
import org.sonatype.aether.RepositoryListener;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.spi.log.Logger;
import org.sonatype.aether.spi.log.NullLogger;

/**
 * A repository listener which purges old snapshots from the local repository when a new snapshot was downloaded.
 */
@Component( role = RepositoryListener.class )
public class SnapshotPurger
    extends AbstractRepositoryListener
{

    @Requirement
    private Logger logger = NullLogger.INSTANCE;

    SnapshotPurger setLogger( Logger logger )
    {
        this.logger = ( logger != null ) ? logger : NullLogger.INSTANCE;
        return this;
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
            String snapshotRegex =
                "\\Q" + snapshotName.substring( 0, snapshot ) + "\\E" + "([0-9]{8}.[0-9]{6}-[0-9]+)" + "\\Q"
                    + snapshotName.substring( snapshot + 8 ) + "\\E" + "(\\.(md5|sha1|lastUpdated))?";

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

}
