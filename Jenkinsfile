#!groovy
pipeline {

  agent {
    docker {
      image 'scmmanager/java-build:11.0.9_11.1'
      label 'docker'
    }
  }

  stages {

    stage('Set Version') {
      when {
        branch pattern: 'release/*', comparator: 'GLOB'
      }
      steps {
        // read version from brach, set it and commit it
        sh "./gradlew setVersion -PnewVersion=${releaseVersion}"
        sh 'git add gradle.properties'
        sh "git -c user.name='CES Marvin' -c user.email='cesmarvin@cloudogu.com' commit -m 'release version ${releaseVersion}'"

        // fetch all remotes from origin
        sh 'git config "remote.origin.fetch" "+refs/heads/*:refs/remotes/origin/*"'
        sh 'git fetch --all'

        // checkout, reset and merge
        sh "git checkout main"
        sh "git reset --hard origin/main"
        sh "git merge --ff-only ${env.BRANCH_NAME}"

          // set tag
        sh "git -c user.name='CES Marvin' -c user.email='cesmarvin@cloudogu.com' tag -m 'release version ${releaseVersion}' ${releaseVersion}"
      }
    }

    stage('Check') {
      steps {
        sh './gradlew check'
        // update timestamp to avoid rerun tests again and fix junit-plugin:
        // ERROR: Test reports were found but none of them are new
        sh 'touch build/test-results/*/*.xml'
        junit 'build/test-results/*/*.xml'
      }
    }

    stage('Build') {
      steps {
        sh './gradlew build'
        archiveArtifacts artifacts: 'build/libs/*.jar'
      }
    }

    stage('Deployment') {
      when {
        branch pattern: 'release/*', comparator: 'GLOB'
      }
      steps {
        withCredentials([usernamePassword(credentialsId: 'plugins.gradle.org-scm-team', passwordVariable: 'GRADLE_SECRET', usernameVariable: 'GRADLE_KEY')]) {
          sh "./gradlew publishPlugins -Dgradle.publish.key=${GRADLE_KEY} -Dgradle.publish.secret=${GRADLE_SECRET}"
        }
      }
    }

    stage('Update Repository') {
      when {
        branch pattern: 'release/*', comparator: 'GLOB'
      }
      steps {
        // merge main in to develop
        sh 'git checkout develop'
        sh 'git merge main'

        // set version to next development iteration
        sh './gradlew setVersionToNextSnapshot'
        sh 'git add gradle.properties'
        sh "git -c user.name='CES Marvin' -c user.email='cesmarvin@cloudogu.com' commit -m 'prepare for next development iteration'"

        // push changes back to remote repository
        withCredentials([usernamePassword(credentialsId: 'cesmarvin-github', usernameVariable: 'GIT_AUTH_USR', passwordVariable: 'GIT_AUTH_PSW')]) {
          sh "git -c credential.helper=\"!f() { echo username='\$GIT_AUTH_USR'; echo password='\$GIT_AUTH_PSW'; }; f\" push origin main --tags"
          sh "git -c credential.helper=\"!f() { echo username='\$GIT_AUTH_USR'; echo password='\$GIT_AUTH_PSW'; }; f\" push origin develop --tags"
          sh "git -c credential.helper=\"!f() { echo username='\$GIT_AUTH_USR'; echo password='\$GIT_AUTH_PSW'; }; f\" push origin :${env.BRANCH_NAME}"
        }
      }
    }

  }
}

String getReleaseVersion() {
  return env.BRANCH_NAME.substring("release/".length());
}
