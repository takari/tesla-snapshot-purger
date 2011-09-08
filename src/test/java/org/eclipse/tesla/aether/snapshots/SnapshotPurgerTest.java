package org.eclipse.tesla.aether.snapshots;

/*******************************************************************************
 * Copyright (c) 2011 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Collections;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;
import org.sonatype.aether.RepositoryEvent;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.impl.internal.SimpleLocalRepositoryManager;
import org.sonatype.aether.repository.LocalRepositoryManager;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.test.impl.SysoutLogger;
import org.sonatype.aether.transfer.ArtifactNotFoundException;
import org.sonatype.aether.util.DefaultRepositorySystemSession;
import org.sonatype.aether.util.artifact.DefaultArtifact;
import org.sonatype.aether.util.listener.DefaultRepositoryEvent;

/**
 */
public class SnapshotPurgerTest
{

    enum Files
    {
        ARTIFACT_ONLY, ARTIFACT_AND_AUXILIARIES
    }

    private static final String GROUP_ID = "gid";

    private static final String ARTIFACT_ID = "aid";

    private static final String[] SNAPSHOTS = { "1.0-20110907.162759-1", "1.0-20110907.182759-23",
        "1.0-20110907.232759-234", "1.0-20110908.002759-1234" };

    @Rule
    public TestName testName = new TestName();

    @Rule
    public TemporaryFolder localRepoDir = new TemporaryFolder();

    private SnapshotPurger purger;

    private DefaultRepositorySystemSession session;

    @Before
    public void setUp()
    {
        System.out.println( "========== " + testName.getMethodName() );

        purger = new SnapshotPurger();
        purger.setLogger( new SysoutLogger() );

        SimpleLocalRepositoryManager lrm = new SimpleLocalRepositoryManager( localRepoDir.getRoot() );
        session = new DefaultRepositorySystemSession().setLocalRepositoryManager( lrm );
    }

    @After
    public void tearDown()
    {
        purger = null;
        session = null;
    }

    private void assertExistent( File... files )
    {
        for ( File file : files )
        {
            assertTrue( "Missing " + file.getAbsolutePath(), file.isFile() );
        }
    }

    private void assertAbsent( File... files )
    {
        for ( File file : files )
        {
            assertFalse( "Existent " + file.getAbsolutePath(), file.exists() );
        }
    }

    private Artifact newArtifact( String ext, String cls, String version )
    {
        Artifact artifact = new DefaultArtifact( GROUP_ID, ARTIFACT_ID, cls, ext, version );

        RemoteRepository repo = new RemoteRepository( "test", "default", "file:" );
        LocalRepositoryManager lrm = session.getLocalRepositoryManager();
        File file = new File( lrm.getRepository().getBasedir(), lrm.getPathForRemoteArtifact( artifact, repo, "" ) );
        artifact = artifact.setFile( file );

        return artifact;
    }

    private Artifact[] newArtifacts( String ext, String cls, String... versions )
    {
        Artifact[] artifacts = new Artifact[versions.length];
        for ( int i = 0; i < versions.length; i++ )
        {
            artifacts[i] = newArtifact( ext, cls, versions[i] );
        }
        return artifacts;
    }

    private File[] createFiles( Files fileSet, Artifact... artifacts )
        throws IOException
    {
        int filesPerArtifact = fileSet == Files.ARTIFACT_AND_AUXILIARIES ? 4 : 1;

        File[] files = new File[artifacts.length * filesPerArtifact];

        for ( int i = 0; i < artifacts.length; i++ )
        {
            Artifact artifact = artifacts[i];

            int index = i * filesPerArtifact;

            // artifact file
            files[index + 0] = artifact.getFile();
            files[index + 0].getAbsoluteFile().getParentFile().mkdirs();
            files[index + 0].createNewFile();

            if ( fileSet == Files.ARTIFACT_AND_AUXILIARIES )
            {
                // checksum files
                files[index + 1] = new File( files[index + 0].getAbsolutePath() + ".md5" );
                files[index + 1].createNewFile();
                files[index + 2] = new File( files[index + 0].getAbsolutePath() + ".sha1" );
                files[index + 2].createNewFile();

                // resolution error status files
                files[index + 3] = new File( files[index + 0].getAbsolutePath() + ".lastUpdated" );
                files[index + 3].createNewFile();
            }
        }

        return files;
    }

    private RepositoryEvent newEvent( Artifact artifact )
    {
        DefaultRepositoryEvent event =
            new DefaultRepositoryEvent( RepositoryEvent.EventType.ARTIFACT_DOWNLOADED, session );
        event.setFile( artifact.getFile() );
        event.setArtifact( artifact );
        if ( event.getFile() == null )
        {
            event.setException( new ArtifactNotFoundException( artifact, null ) );
        }

        return event;
    }

    private void notify( Artifact artifact )
    {
        purger.artifactDownloaded( newEvent( artifact ) );
    }

    @Test
    public void testDoNotCrashWhenEventHasNoFileDueToDownloadFailure()
    {
        notify( newArtifact( "jar", "", SNAPSHOTS[0] ).setFile( null ) );
    }

    @Test
    public void testDoNotDeleteCurrentSnapshot()
        throws Exception
    {
        Artifact artifact = newArtifact( "jar", "", SNAPSHOTS[0] );
        File[] files = createFiles( Files.ARTIFACT_AND_AUXILIARIES, artifact );
        notify( artifact );
        assertExistent( files );
    }

    @Test
    public void testDeleteOldSnapshots()
        throws Exception
    {
        Artifact newArtifact = newArtifact( "jar", "", SNAPSHOTS[3] );
        Artifact[] oldArtifacts = newArtifacts( "jar", "", SNAPSHOTS[0], SNAPSHOTS[1], SNAPSHOTS[2] );
        File[] newFiles = createFiles( Files.ARTIFACT_ONLY, newArtifact );
        File[] oldFiles = createFiles( Files.ARTIFACT_ONLY, oldArtifacts );
        notify( newArtifact );
        assertExistent( newFiles );
        assertAbsent( oldFiles );
    }

    @Test
    public void testDeleteAuxiliaryFilesOfOldSnapshotsAsWell()
        throws Exception
    {
        Artifact newArtifact = newArtifact( "jar", "", SNAPSHOTS[3] );
        Artifact[] oldArtifacts = newArtifacts( "jar", "", SNAPSHOTS[0], SNAPSHOTS[1], SNAPSHOTS[2] );
        File[] newFiles = createFiles( Files.ARTIFACT_ONLY, newArtifact );
        File[] oldFiles = createFiles( Files.ARTIFACT_AND_AUXILIARIES, oldArtifacts );
        notify( newArtifact );
        assertExistent( newFiles );
        assertAbsent( oldFiles );
    }

    @Test
    public void testDoNotDeleteSnapshotsOfOtherArtifacts()
        throws Exception
    {
        Artifact artifact = newArtifact( "jar", "", SNAPSHOTS[1] );
        Artifact artifact1 = newArtifact( "pom", "", SNAPSHOTS[0] );
        Artifact artifact2 = newArtifact( "jar", "sources", SNAPSHOTS[0] );
        Artifact artifact3 = newArtifact( "jar", "javadoc", SNAPSHOTS[0] );
        File[] newFiles = createFiles( Files.ARTIFACT_ONLY, artifact );
        File[] oldFiles = createFiles( Files.ARTIFACT_AND_AUXILIARIES, artifact1, artifact2, artifact3 );
        notify( artifact );
        assertExistent( newFiles );
        assertExistent( oldFiles );
    }

    @Test
    public void testDoNotChokeUponFailureToDeleteOldSnapshot()
        throws Exception
    {
        Artifact newArtifact = newArtifact( "jar", "", SNAPSHOTS[1] );
        Artifact oldArtifact = newArtifact( "jar", "", SNAPSHOTS[0] );
        createFiles( Files.ARTIFACT_ONLY, newArtifact );
        File[] oldFiles = createFiles( Files.ARTIFACT_ONLY, oldArtifact );
        RandomAccessFile raf = new RandomAccessFile( oldFiles[0], "rw" );
        try
        {
            notify( newArtifact );
        }
        finally
        {
            raf.close();
        }
    }

    @Test
    public void testDoNotDeleteExcludedSnapshots()
        throws Exception
    {
        session.setConfigProperty( SnapshotPurger.PROPERTY_EXCLUDES, GROUP_ID + ":" + ARTIFACT_ID );
        Artifact newArtifact = newArtifact( "jar", "", SNAPSHOTS[3] );
        Artifact[] oldArtifacts = newArtifacts( "jar", "", SNAPSHOTS[0], SNAPSHOTS[1], SNAPSHOTS[2] );
        File[] newFiles = createFiles( Files.ARTIFACT_ONLY, newArtifact );
        File[] oldFiles = createFiles( Files.ARTIFACT_ONLY, oldArtifacts );
        notify( newArtifact );
        assertExistent( newFiles );
        assertExistent( oldFiles );
    }

    @Test
    public void testIsMatch()
    {
        assertEquals( true, purger.isMatch( "test", "test" ) );
        assertEquals( true, purger.isMatch( "org.eclipse.tesla", "org.eclipse.tesla" ) );
        assertEquals( false, purger.isMatch( "org.eclipse_tesla", "org.eclipse.tesla" ) );
        assertEquals( true, purger.isMatch( "org.eclipse.tesla", "?rg.eclipse.tesla" ) );
        assertEquals( true, purger.isMatch( "org.eclipse.tesla", "*" ) );
        assertEquals( true, purger.isMatch( "org.eclipse.tesla", "org.*" ) );
        assertEquals( false, purger.isMatch( "com.eclipse.tesla", "org.*" ) );
    }

    @Test
    public void testIsMatched()
    {
        Artifact artifact = newArtifact( "jar", "", "1.0" );
        assertEquals( false, purger.isMatched( artifact, "" ) );
        assertEquals( true, purger.isMatched( artifact, GROUP_ID ) );
        assertEquals( false, purger.isMatched( artifact, GROUP_ID + "fail" ) );
        assertEquals( true, purger.isMatched( artifact, GROUP_ID + ":*" ) );
        assertEquals( true, purger.isMatched( artifact, GROUP_ID + ":" + ARTIFACT_ID ) );
        assertEquals( true, purger.isMatched( artifact, "*:" + ARTIFACT_ID ) );
        assertEquals( false, purger.isMatched( artifact, GROUP_ID + ":" + ARTIFACT_ID + "fail" ) );
        assertEquals( false, purger.isMatched( artifact, GROUP_ID + ":" + ARTIFACT_ID + ":fail" ) );
    }

    @Test
    public void testIsExcluded()
    {
        Artifact artifact = newArtifact( "jar", "", "1.0" );
        assertEquals( false, purger.isExcluded( artifact, config( null ) ) );
        assertEquals( false, purger.isExcluded( artifact, config( "" ) ) );
        assertEquals( false, purger.isExcluded( artifact, config( ",,," ) ) );
        assertEquals( true, purger.isExcluded( artifact, config( GROUP_ID ) ) );
        assertEquals( true, purger.isExcluded( artifact, config( ", " + GROUP_ID + " , " ) ) );
        assertEquals( true, purger.isExcluded( artifact, config( "foo," + GROUP_ID + ",bar" ) ) );
        assertEquals( true, purger.isExcluded( artifact, config( "foo:foo," + GROUP_ID + ":" + ARTIFACT_ID ) ) );
    }

    private Map<String, Object> config( String excludes )
    {
        return Collections.<String, Object> singletonMap( SnapshotPurger.PROPERTY_EXCLUDES, excludes );
    }

}
