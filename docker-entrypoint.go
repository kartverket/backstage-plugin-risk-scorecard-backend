package main

import (
	"fmt"
	"os"
	"os/exec"
	"os/signal"
	"syscall"
)

func main() {
	// Check if LOCAL environment variable is set
	if os.Getenv("LOCAL") != "" {
		fmt.Println("Starting socat relay service")
		
		// Start socat relay for port 7007
		cmd1 := exec.Command("/usr/bin/socat", "tcp-l:7007,fork", "tcp:host.docker.internal:7007")
		cmd1.Stdout = os.Stdout
		cmd1.Stderr = os.Stderr
		go func() {
			if err := cmd1.Run(); err != nil {
				fmt.Fprintf(os.Stderr, "socat 7007 error: %v\n", err)
			}
		}()

		// Start socat relay for port 8085
		cmd2 := exec.Command("/usr/bin/socat", "tcp-l:8085,fork", "tcp:host.docker.internal:8085")
		cmd2.Stdout = os.Stdout
		cmd2.Stderr = os.Stderr
		go func() {
			if err := cmd2.Run(); err != nil {
				fmt.Fprintf(os.Stderr, "socat 8085 error: %v\n", err)
			}
		}()

		// Wait for interrupt signal
		sigChan := make(chan os.Signal, 1)
		signal.Notify(sigChan, syscall.SIGINT, syscall.SIGTERM)
		go func() {
			<-sigChan
			if cmd1.Process != nil {
				cmd1.Process.Kill()
			}
			if cmd2.Process != nil {
				cmd2.Process.Kill()
			}
			os.Exit(0)
		}()
	}

	// Execute the main process
	args := os.Args[1:]
	
	cmd := exec.Command(args[0], args[1:]...)
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	cmd.Stdin = os.Stdin
	
	if err := cmd.Run(); err != nil {
		if exitErr, ok := err.(*exec.ExitError); ok {
			os.Exit(exitErr.ExitCode())
		}
		os.Exit(1)
	}
}