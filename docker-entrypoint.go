package main

import (
	"fmt"
	"os"
	"os/exec"
	"syscall"
)

func main() {
	if os.Getenv("LOCAL") != "" {
		fmt.Println("Starting socat relay service")

		cmd1 := exec.Command("/usr/bin/socat", "tcp-l:7007,fork", "tcp:host.docker.internal:7007")
		cmd1.Stdout = os.Stdout
		cmd1.Stderr = os.Stderr
		if err := cmd1.Start(); err != nil {
			fmt.Fprintf(os.Stderr, "socat 7007 error: %v\n", err)
		}

		cmd2 := exec.Command("/usr/bin/socat", "tcp-l:8085,fork", "tcp:host.docker.internal:8085")
		cmd2.Stdout = os.Stdout
		cmd2.Stderr = os.Stderr
		if err := cmd2.Start(); err != nil {
			fmt.Fprintf(os.Stderr, "socat 8085 error: %v\n", err)
		}
	}

	// Replace this process with the main process (e.g. Java).
	// This makes the child PID 1 so it receives signals directly.
	args := os.Args[1:]
	binary, err := exec.LookPath(args[0])
	if err != nil {
		fmt.Fprintf(os.Stderr, "could not find binary %s: %v\n", args[0], err)
		os.Exit(1)
	}
	if err := syscall.Exec(binary, args, os.Environ()); err != nil {
		fmt.Fprintf(os.Stderr, "exec failed: %v\n", err)
		os.Exit(1)
	}
}

