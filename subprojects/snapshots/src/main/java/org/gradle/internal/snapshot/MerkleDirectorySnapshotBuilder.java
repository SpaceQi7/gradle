/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.snapshot;

import org.gradle.internal.file.FileMetadata.AccessType;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot.FileSystemLocationSnapshotVisitor;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class MerkleDirectorySnapshotBuilder {
    private static final HashCode DIR_SIGNATURE = Hashing.signature("DIR");

    private final Deque<List<CompleteFileSystemLocationSnapshot>> levelHolder = new ArrayDeque<>();
    private final boolean sortingRequired;

    public static MerkleDirectorySnapshotBuilder sortingRequired() {
        return new MerkleDirectorySnapshotBuilder(true);
    }

    public static MerkleDirectorySnapshotBuilder noSortingRequired() {
        return new MerkleDirectorySnapshotBuilder(false);
    }

    private MerkleDirectorySnapshotBuilder(boolean sortingRequired) {
        this.sortingRequired = sortingRequired;
        addLevel();
    }

    public void preVisitDirectory() {
        addLevel();
    }

    public void visitEntry(CompleteFileSystemLocationSnapshot snapshot) {
        snapshot.accept(new FileSystemLocationSnapshotVisitor() {
            @Override
            public void visitRegularFile(RegularFileSnapshot fileSnapshot) {
                collectResult(snapshot);
            }

            @Override
            public void visitMissing(MissingFileSnapshot missingSnapshot) {
                collectResult(snapshot);
            }
        });
    }

    public boolean postVisitDirectory(boolean includeEmpty, AccessType accessType, String absolutePath, String name) {
        List<CompleteFileSystemLocationSnapshot> children = levelHolder.removeLast();
        if (children.isEmpty() && !includeEmpty) {
            return false;
        }
        if (sortingRequired) {
            children.sort(CompleteFileSystemLocationSnapshot.BY_NAME);
        }
        Hasher hasher = Hashing.newHasher();
        hasher.putHash(DIR_SIGNATURE);
        for (CompleteFileSystemLocationSnapshot child : children) {
            hasher.putString(child.getName());
            hasher.putHash(child.getHash());
        }
        collectResult(new CompleteDirectorySnapshot(absolutePath, name, accessType, hasher.hash(), children));
        return true;
    }

    private void addLevel() {
        levelHolder.addLast(new ArrayList<>());
    }

    private void collectResult(CompleteFileSystemLocationSnapshot snapshot) {
        List<CompleteFileSystemLocationSnapshot> siblings = levelHolder.peekLast();
        if (siblings == null) {
            throw new IllegalStateException("Outside of root");
        }
        siblings.add(snapshot);
    }

    @Nullable
    public CompleteFileSystemLocationSnapshot getResult() {
        assert levelHolder.size() == 1;
        List<CompleteFileSystemLocationSnapshot> rootLevel = levelHolder.getLast();
        if (rootLevel.isEmpty()) {
            return null;
        } else {
            assert rootLevel.size() == 1;
            return rootLevel.get(0);
        }
    }
}
